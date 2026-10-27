package vn.haui.dx.apps.servicedesk.intelligence;

import org.springframework.stereotype.Component;
import vn.haui.dx.apps.servicedesk.intelligence.assessment.AssessmentPolicy;
import vn.haui.dx.apps.servicedesk.intelligence.assessment.ProblemAssessor;
import vn.haui.dx.apps.servicedesk.intelligence.context.ContextProjection;
import vn.haui.dx.apps.servicedesk.intelligence.context.ContextSnapshot;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Chạy factual AI07 trên từng nguồn; đính chính không nối quote sang nguồn khác. */
@Component
public class ContextualFacts {
    public record Result(String impact, String urgency, String priority, List<String> riskFlags,
            List<ContextProjection.Evidence> evidence, List<String> reasons) {}

    private final ProblemAssessor assessor;
    private final AssessmentPolicy assessment;
    private final TicketAssessmentInputAssembler inputs;

    public ContextualFacts(ProblemAssessor assessor, AssessmentPolicy assessment, TicketAssessmentInputAssembler inputs) {
        this.assessor = assessor;
        this.assessment = assessment;
        this.inputs = inputs;
    }

    public Result assess(ContextSnapshot snapshot, ContextProjection context) {
        var impacts = new LinkedHashSet<String>();
        var urgencies = new LinkedHashSet<String>();
        var risk = new LinkedHashSet<String>();
        var evidence = new ArrayList<ContextProjection.Evidence>();
        var reasons = new ArrayList<String>();
        long impactCorrection = correction(context, List.of("affectedCount", "affectedScope"));
        long urgencyCorrection = correction(context, List.of("workBlocked", "workaroundAvailable", "timeWindow"));
        for (var source : snapshot.sources()) {
            var facts = assessor.assess(Map.of("title", source.text()));
            risk.addAll(facts.risk().flags());
            boolean impactSource = source.slot() == null || List.of("affectedCount", "affectedScope").contains(source.slot());
            boolean urgencySource = source.slot() == null || List.of("workBlocked", "workaroundAvailable", "timeWindow").contains(source.slot());
            if (impactSource && source.revision() >= impactCorrection && "CONFLICT".equals(facts.impact().status())) {
                reasons.add("IMPACT_CONFLICT");
            }
            if (urgencySource && source.revision() >= urgencyCorrection && "CONFLICT".equals(facts.urgency().status())) {
                reasons.add("URGENCY_CONFLICT");
            }
            if (impactSource && source.revision() >= impactCorrection && facts.impact().value() != null) {
                impacts.add(facts.impact().value());
                facts.impact().evidence().forEach(span -> evidence.add(map(source, span)));
            }
            if (urgencySource && source.revision() >= urgencyCorrection && facts.urgency().value() != null) {
                urgencies.add(facts.urgency().value());
                facts.urgency().evidence().forEach(span -> evidence.add(map(source, span)));
            }
            facts.evidence().stream().filter(span -> span.supports().contains("risk"))
                    .forEach(span -> evidence.add(map(source, span)));
        }
        var scope = context.slots().get("affectedScope");
        if (context.usable("affectedScope") && fresh(scope, impactCorrection)) {
            add(impacts, inputs.policy().path("impactByScope").path(scope.value().toString()).textValue(), scope, evidence);
        } else if (context.value("affectedCount") instanceof Integer count && fresh(context.slots().get("affectedCount"), impactCorrection)) {
            add(impacts, count == 1 ? "I1" : "I2", context.slots().get("affectedCount"), evidence);
        }
        for (String slot : List.of("workBlocked", "workaroundAvailable")) {
            if (Boolean.TRUE.equals(context.value(slot)) && fresh(context.slots().get(slot), urgencyCorrection)) {
                add(urgencies, inputs.policy().path("urgencyByTrue").path(slot).asText(), context.slots().get(slot), evidence);
            }
        }
        if (impacts.size() > 1) { reasons.add("IMPACT_CONFLICT"); }
        if (urgencies.size() > 1) { reasons.add("URGENCY_CONFLICT"); }
        if (evidence.size() > 64) {
            return new Result(null, null, null, List.copyOf(risk), List.of(), List.of("FACT_BUDGET_EXCEEDED"));
        }
        String impact = impacts.size() == 1 && !reasons.contains("IMPACT_CONFLICT") ? impacts.iterator().next() : null;
        String urgency = urgencies.size() == 1 && !reasons.contains("URGENCY_CONFLICT") ? urgencies.iterator().next() : null;
        return new Result(impact, urgency, assessment.priority(impact, urgency), List.copyOf(risk), List.copyOf(evidence), List.copyOf(reasons));
    }

    private static boolean fresh(ContextProjection.Slot slot, long correction) {
        return slot.evidence().stream().mapToLong(ContextProjection.Evidence::sourceRevision).max().orElse(0) >= correction;
    }

    private static long correction(ContextProjection context, List<String> slots) {
        return slots.stream().filter(context::usable).flatMap(slot -> context.slots().get(slot).evidence().stream())
                .filter(ContextProjection.Evidence::correction).mapToLong(ContextProjection.Evidence::sourceRevision).max().orElse(0);
    }

    private static void add(Set<String> values, String value, ContextProjection.Slot slot, List<ContextProjection.Evidence> evidence) {
        if (value != null && !value.isBlank() && !slot.evidence().isEmpty()) {
            values.add(value);
            evidence.add(slot.evidence().get(slot.evidence().size() - 1));
        }
    }

    private static ContextProjection.Evidence map(
            ContextSnapshot.Source source, vn.haui.dx.apps.servicedesk.intelligence.assessment.AssessmentEvidence span) {
        var evidence = new ContextProjection.Evidence(source.sourceId(), source.revision(), span.quote(), span.start(), span.end(), span.ruleId(), source.correction());
        if (!evidence.verifies(source.text())) {
            throw new IllegalStateException("Invalid contextual factual evidence");
        }
        return evidence;
    }
}
