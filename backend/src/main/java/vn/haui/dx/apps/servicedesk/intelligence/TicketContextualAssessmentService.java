package vn.haui.dx.apps.servicedesk.intelligence;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import vn.haui.dx.apps.servicedesk.intelligence.assessment.AssessmentPolicy;
import vn.haui.dx.apps.servicedesk.intelligence.context.*;
import vn.haui.dx.hpdi.common.api.CapabilityException;
import vn.haui.dx.hpdi.intelligence.api.ClassificationCapability;
import vn.haui.dx.hpdi.intelligence.api.ClassificationInput;
import vn.haui.dx.hpdi.intelligence.api.ClassificationObservation;
import vn.haui.dx.hpdi.intelligence.api.IntelligenceFailure;
import vn.haui.dx.hpdi.process.api.ProcessObservations;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class TicketContextualAssessmentService {
    public static final String VERSION = "ticket-assessment-contextual@0.1.0";
    public record Ai07(String category, String impact, String urgency, String priority, List<String> riskFlags,
            Double confidence, String confidenceSemantics) {}
    public record Provenance(String modelDigest, String modelPolicyDigest, String contextualPolicyDigest,
            String assessmentPolicyDigest, String inputDigest, String evidenceStatus) {}
    public record View(String contractVersion, UUID ticketId, long contextRevision, String contextDigest,
            String assessmentStatus, ContextReadinessView.State dataReadiness, Ai07 ai07,
            List<ContextProjection.Evidence> evidence, List<String> reasons, Provenance provenance,
            boolean needsReview, boolean operationalAuthority, boolean workflowGateApplied) {}
    private record Model(String status, String category, String digest) {}

    private final ContextEvaluationGuard guard;
    private final ContextSlotExtractor extractor;
    private final ContextReadinessPolicy readiness;
    private final TicketAssessmentInputAssembler inputs;
    private final ContextualFacts facts;
    private final ClassificationCapability classifier;
    private final ClassifierConfiguration.Settings settings;
    private final ClassifierProcessingGate modelRights;
    private final ContextProcessingGate contextRights;
    private final AssessmentPolicy assessment;
    private final ContextualAttemptStore attempts;
    private final ProcessObservations processes;
    private final boolean enabled;
    private final String approvedPolicyDigest;
    private final org.springframework.beans.factory.ObjectProvider<vn.haui.dx.apps.servicedesk.intake.IntakeAdmission> intake;

    public TicketContextualAssessmentService(
            ContextEvaluationGuard guard, ContextSlotExtractor extractor, ContextReadinessPolicy readiness,
            TicketAssessmentInputAssembler inputs, ContextualFacts facts, ClassificationCapability classifier,
            ClassifierConfiguration.Settings settings, ClassifierProcessingGate modelRights, ContextProcessingGate contextRights,
            AssessmentPolicy assessment, ContextualAttemptStore attempts, ProcessObservations processes,
            @Value("${serviceDesk.contextual-assessment.enabled:false}") boolean enabled,
            @Value("${serviceDesk.contextual-assessment.policy-digest:}") String approvedPolicyDigest,
            org.springframework.beans.factory.ObjectProvider<vn.haui.dx.apps.servicedesk.intake.IntakeAdmission> intake) {
        this.guard = guard;
        this.extractor = extractor;
        this.readiness = readiness;
        this.inputs = inputs;
        this.facts = facts;
        this.classifier = classifier;
        this.settings = settings;
        this.modelRights = modelRights;
        this.contextRights = contextRights;
        this.assessment = assessment;
        this.attempts = attempts;
        this.processes = processes;
        this.enabled = enabled;
        this.approvedPolicyDigest = approvedPolicyDigest;
        this.intake = intake;
    }

    public View assess(String workspace, UUID id) {
        if (intake.getObject().find(workspace, id) != null) {
            throw CapabilityException.conflict("Intake final assessment is processed only by the sealed background job");
        }
        return evaluate(workspace, id);
    }

    public View assessFinal(String workspace, UUID id, long revision, String digest) {
        var state = intake.getObject().find(workspace, id);
        if (state == null || !"ASSESSING".equals(state.status()) || state.sealedRevision() == null
                || state.sealedRevision() != revision || !java.util.Objects.equals(digest, state.contextDigest())) {
            throw CapabilityException.conflict("Final assessment requires current sealed intake");
        }
        return evaluate(workspace, id);
    }

    private View evaluate(String workspace, UUID id) {
        return guard.evaluate(workspace, id, session -> {
            if (session.shadow()) {
                return new View(VERSION, id, session.snapshot().contextRevision(), null, "SHADOW", null,
                        null, List.of(), List.of("SHADOW_NOT_PUBLISHED"), null, true, false, false);
            }
            var processBefore = processes.snapshots(workspace, Set.of(session.ticket().process().processInstanceId()));
            String sourceGrant = contextRights.verifyContextual(workspace, session.scope(), readiness.digest(), inputs.policyDigest());
            var context = extractor.extract(session.snapshot());
            var ready = readiness.evaluate(session.snapshot(), context, 0);
            var actualFacts = facts.assess(session.snapshot(), context);
            var input = inputs.assemble(session.snapshot(), context);
            var reasons = new ArrayList<>(actualFacts.reasons());
            reasons.addAll(ready.reasonCodes());
            reasons.add("USER_REPORTED_CONTEXT");
            reasons.add("DRAFT_POLICY_REQUIRES_REVIEW");
            Model model;
            if (!enabled) {
                model = new Model("DETERMINISTIC_ONLY", null, null);
                reasons.add("MODEL_VALIDATION_REQUIRED");
            } else if (ready.overall() == ContextReadinessView.State.MANUAL_REVIEW_REQUIRED
                    || ready.overall() == ContextReadinessView.State.ENRICHMENT_REQUIRED || input.unsafe()
                    || actualFacts.reasons().contains("FACT_BUDGET_EXCEEDED")) {
                model = new Model("ENRICHMENT_REQUIRED", null, null);
                reasons.add("MODEL_CALL_SKIPPED_BY_READINESS");
            } else {
                model = classify(session, input);
            }
            inputs.verifyUnchanged();
            if (!java.util.Objects.equals(sourceGrant, contextRights.verifyContextual(
                    workspace, session.scope(), readiness.digest(), inputs.policyDigest()))) {
                throw CapabilityException.conflict("Contextual processing permission changed");
            }
            if (!processBefore.equals(processes.snapshots(workspace, Set.of(session.ticket().process().processInstanceId())))) {
                throw CapabilityException.conflict("Process changed during contextual assessment");
            }
            if (model.category() == null) { reasons.add("CATEGORY_UNCONFIRMED"); }
            if ("DEGRADED".equals(model.status())) { reasons.add("AI_PROVIDER_UNAVAILABLE"); }
            return new View(VERSION, id, session.snapshot().contextRevision(), session.snapshot().digest(), model.status(),
                    ready.overall(), new Ai07(model.category(), actualFacts.impact(), actualFacts.urgency(), actualFacts.priority(),
                            actualFacts.riskFlags(), null, "NOT_CALIBRATED"), actualFacts.evidence(), List.copyOf(reasons),
                    new Provenance(model.digest(), model.digest() == null ? null : settings.policyDigest(), inputs.policyDigest(),
                            assessment.bundleDigest(), input.inputDigest(), "USER_REPORTED_NOT_VERIFIED"), true, false, false);
        });
    }

    private Model classify(ContextEvaluationGuard.Session session, TicketAssessmentInputAssembler.Input input) {
        if (settings.mode() != ClassifierConfiguration.Mode.ASSISTED
                || !inputs.policyDigest().equals(approvedPolicyDigest)
                || !settings.modelDigest().matches("[0-9a-f]{64}") || !settings.policyDigest().matches("[0-9a-f]{64}")) {
            throw CapabilityException.forbidden("An explicitly pinned contextual model policy is required");
        }
        String workspace = session.snapshot().workspace();
        String contextGrant = contextRights.verifyContextual(workspace, session.scope(), readiness.digest(), inputs.policyDigest());
        String modelGrant = modelRights.verifyAssessment(workspace, assessment.bundleDigest());
        String processingDigest = ContextPolicyCatalog.digest(session.actor().subject() + ":" + session.scope() + ":" + contextGrant + ":" + modelGrant);
        var key = new ContextualAttemptStore.Key(workspace, session.snapshot().ticketId(), input.inputDigest(), processingDigest,
                inputs.policyDigest(), settings.modelDigest());
        var claim = attempts.admit(key);
        Model result;
        if (claim.isEmpty()) {
            var cached = attempts.find(key).orElseThrow(IntelligenceFailure::invalid);
            result = new Model("DONE".equals(cached.state()) ? "MODEL_REUSED_MATERIAL" : "DEGRADED",
                    "DONE".equals(cached.state()) ? cached.category() : null, settings.modelDigest());
        } else {
            try {
                var value = classifier.observe(workspace, new ClassificationInput(input.fields(),
                        List.of("ACCOUNT", "HARDWARE", "NETWORK", "SOFTWARE", "OTHER"), settings.modelDigest(), settings.policyDigest()));
                validate(value);
                String category = "PROPOSE".equals(value.decision()) ? value.classId() : null;
                attempts.finish(key, claim.get(), "DONE", category);
                result = new Model("REVIEW_REQUIRED", category, settings.modelDigest());
            } catch (IntelligenceFailure failure) {
                attempts.finish(key, claim.get(), "DEGRADED", null);
                result = new Model("DEGRADED", null, settings.modelDigest());
            }
        }
        if (!contextGrant.equals(contextRights.verifyContextual(workspace, session.scope(), readiness.digest(), inputs.policyDigest()))
                || !modelGrant.equals(modelRights.verifyAssessment(workspace, assessment.bundleDigest()))) {
            throw CapabilityException.conflict("Processing grant changed during contextual inference");
        }
        return result;
    }

    private void validate(ClassificationObservation value) {
        if (value == null || value.operationalAuthority() || !settings.modelDigest().equals(value.modelDigest())
                || !settings.policyDigest().equals(value.policyDigest()) || !"UNVERIFIED_BY_MODEL".equals(value.evidenceStatus())
                || !"NOT_AVAILABLE".equals(value.scoreSemantics())
                || !("ABSTAIN".equals(value.decision()) && value.classId() == null
                        || "PROPOSE".equals(value.decision()) && value.classId() != null
                                && List.of("ACCOUNT", "HARDWARE", "NETWORK", "SOFTWARE", "OTHER").contains(value.classId()))) {
            throw IntelligenceFailure.invalid();
        }
    }
}
