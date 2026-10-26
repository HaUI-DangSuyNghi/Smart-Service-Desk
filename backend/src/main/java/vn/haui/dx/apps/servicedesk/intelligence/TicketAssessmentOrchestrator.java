package vn.haui.dx.apps.servicedesk.intelligence;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import vn.haui.dx.apps.servicedesk.Ticket;
import vn.haui.dx.apps.servicedesk.intelligence.assessment.AssessmentEvidence;
import vn.haui.dx.apps.servicedesk.intelligence.assessment.AssessmentPolicy;
import vn.haui.dx.apps.servicedesk.intelligence.assessment.ProblemAssessor;
import vn.haui.dx.apps.servicedesk.intelligence.taxonomy.SubcategoryResolver;
import vn.haui.dx.hpdi.common.api.CapabilityException;
import vn.haui.dx.hpdi.intelligence.api.IntelligenceFailure;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.TreeSet;
import java.util.UUID;
import java.util.function.LongSupplier;

@Service
public final class TicketAssessmentOrchestrator {
    private final TicketClassificationOrchestrator classifier;
    private final AssessmentPolicy policy;
    private final SubcategoryResolver subcategories;
    private final ProblemAssessor assessor;
    private final boolean enabled;
    private final String bundleDigest;
    private final ClassifierProcessingGate processing;
    private final LongSupplier clock;

    @org.springframework.beans.factory.annotation.Autowired
    public TicketAssessmentOrchestrator(
            TicketClassificationOrchestrator classifier,
            AssessmentPolicy policy,
            SubcategoryResolver subcategories,
            ProblemAssessor assessor,
            ClassifierProcessingGate processing,
            @Value("${serviceDesk.assessment.enabled:false}") boolean enabled,
            @Value("${serviceDesk.assessment.policy-bundle-digest:}") String bundleDigest) {
        this(
                classifier,
                policy,
                subcategories,
                assessor,
                processing,
                enabled,
                bundleDigest,
                System::nanoTime);
    }

    TicketAssessmentOrchestrator(
            TicketClassificationOrchestrator classifier,
            AssessmentPolicy policy,
            SubcategoryResolver subcategories,
            ProblemAssessor assessor,
            ClassifierProcessingGate processing,
            boolean enabled,
            String bundleDigest,
            LongSupplier clock) {
        this.classifier = classifier;
        this.policy = policy;
        this.subcategories = subcategories;
        this.assessor = assessor;
        this.enabled = enabled;
        this.bundleDigest = bundleDigest;
        this.processing = processing;
        this.clock = clock;
    }

    public TicketAssessmentView assess(String workspace, UUID id) {
        if (!enabled) {
            throw CapabilityException.notFound("Assessment is disabled");
        }
        if (!policy.bundleDigest().equals(bundleDigest)) {
            throw IntelligenceFailure.configuration();
        }
        policy.verifyUnchanged();
        long started = clock.getAsLong();
        var result =
                classifier.withSnapshot(
                        workspace,
                        id,
                        (snapshot, classification) -> {
                            checkDeadline(started, 5);
                            long compositionStarted = clock.getAsLong();
                            var composed = compose(snapshot, classification);
                            policy.verifyUnchanged();
                            checkDeadline(compositionStarted, 1);
                            return composed;
                        },
                        () -> processing.verifyAssessment(workspace, bundleDigest));
        checkDeadline(started, 5);
        return result;
    }

    private void checkDeadline(long started, long seconds) {
        if (clock.getAsLong() - started > Duration.ofSeconds(seconds).toNanos()) {
            throw IntelligenceFailure.timeout();
        }
    }

    private TicketAssessmentView compose(Ticket snapshot, TicketClassificationView model) {
        var fields = new LinkedHashMap<String, String>();
        fields.put("title", snapshot.title());
        fields.put("description", snapshot.description());
        boolean shadow = "SHADOW".equals(model.mode());
        boolean degraded = "DEGRADED".equals(model.status());
        var reasons = new TreeSet<String>();
        reasons.addAll(model.policyDisposition().reasonCodes());
        reasons.add("DRAFT_POLICY_REQUIRES_REVIEW");
        var evidence = new ArrayList<AssessmentEvidence>();
        var attributes = new ArrayList<TicketAssessmentView.Attribute>();
        var missing = new ArrayList<String>();
        var questions = new ArrayList<String>();
        TicketAssessmentView.Classification classification = null;
        var assessment =
                new TicketAssessmentView.Assessment(
                        null,
                        null,
                        null,
                        new ProblemAssessor.Risk("NOT_ASSESSED", null, List.of(), true));
        if (!shadow) {
            String parent = model.classification().category();
            // Instructions/conflicting narrative are review-only, not factual extraction sources.
            boolean unsafe = reasons.contains("SAFETY_GATE") || reasons.contains("REVIEW_CONFLICT");
            var child = subcategories.resolve(degraded || unsafe ? null : parent, fields);
            classification =
                    new TicketAssessmentView.Classification(
                            model.classification().decision(),
                            parent,
                            child.child(),
                            child.status(),
                            List.of(),
                            null,
                            "NOT_CALIBRATED");
            if (!degraded && !unsafe) {
                var facts = assessor.assess(fields);
                assessment =
                        new TicketAssessmentView.Assessment(
                                facts.impact().value(),
                                facts.urgency().value(),
                                facts.priority(),
                                facts.risk());
                evidence.addAll(child.evidence());
                evidence.addAll(facts.evidence());
                addAttributes(
                        attributes,
                        facts.impact().value(),
                        facts.impact().evidence(),
                        "affectedScope");
                String urgencySlot =
                        switch (facts.urgency().value() == null ? "" : facts.urgency().value()) {
                            case "U1" -> "workaroundAvailable";
                            case "U3" -> "workBlocked";
                            default -> "timeEvidence";
                        };
                addAttributes(
                        attributes,
                        facts.urgency().value(),
                        facts.urgency().evidence(),
                        urgencySlot);
                for (var span : facts.evidence()) {
                    if (span.supports().contains("risk")) {
                        attributes.add(
                                new TicketAssessmentView.Attribute(
                                        "securitySignals",
                                        span.ruleId().substring("RISK-".length()),
                                        span));
                    }
                }
                if ("CONFLICT".equals(facts.impact().status())) {
                    reasons.add("IMPACT_CONFLICT");
                }
                if ("CONFLICT".equals(facts.urgency().status())) {
                    reasons.add("URGENCY_CONFLICT");
                }
                if (!facts.risk().flags().isEmpty()) {
                    reasons.add("RISK_REQUIRES_REVIEW");
                }
            }
            if (!"SUPPORTED_BY_RULE".equals(child.status())) {
                reasons.add(child.status());
            }
            for (String slot : policy.requiredSlots(parent)) {
                boolean absent =
                        switch (slot) {
                            case "impact" -> assessment.impact() == null;
                            case "urgency" -> assessment.urgency() == null;
                            default -> child.child() == null;
                        };
                if (absent) {
                    missing.add(slot);
                }
            }
            missing.forEach(slot -> questions.add(policy.question(slot)));
        }
        if (evidence.size() > 64 || attributes.size() > 64) {
            // Never truncate evidence while retaining a conclusion it may no longer support.
            throw IntelligenceFailure.invalid();
        }
        for (var span : evidence) {
            if (!span.verifies(fields.get(span.field()))) {
                throw IntelligenceFailure.invalid();
            }
        }
        var identities = policy.digests();
        return new TicketAssessmentView(
                TicketAssessmentView.VERSION,
                model.mode(),
                model.status(),
                classification,
                assessment,
                attributes,
                evidence,
                missing,
                questions,
                new TicketAssessmentView.Disposition(true, false, false, List.copyOf(reasons)),
                new TicketAssessmentView.Provenance(
                        classifier.modelDigest(),
                        classifier.modelPolicyDigest(),
                        "servicedesk-taxonomy@0.1.0",
                        identities.get("taxonomy"),
                        "servicedesk-assessment@0.1.0",
                        identities.get("assessment"),
                        identities.get("priority"),
                        "DRAFT",
                        TicketAssessmentView.VERSION));
    }

    private static void addAttributes(
            List<TicketAssessmentView.Attribute> attributes,
            String value,
            List<AssessmentEvidence> spans,
            String slot) {
        if (value != null) {
            spans.forEach(
                    span -> attributes.add(new TicketAssessmentView.Attribute(slot, value, span)));
        }
    }
}
