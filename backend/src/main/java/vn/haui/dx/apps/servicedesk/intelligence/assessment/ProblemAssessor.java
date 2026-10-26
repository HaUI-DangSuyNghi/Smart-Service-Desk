package vn.haui.dx.apps.servicedesk.intelligence.assessment;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Each axis uses its own factual signals and has no access to a model category. */
@Component
public final class ProblemAssessor {
    public record Axis(String value, String status, List<AssessmentEvidence> evidence) {
        public Axis {
            evidence = List.copyOf(evidence);
        }
    }

    public record Risk(String status, String level, List<String> flags, boolean reviewRequired) {
        public Risk {
            flags = List.copyOf(flags);
        }
    }

    public record Result(
            Axis impact,
            Axis urgency,
            String priority,
            Risk risk,
            List<AssessmentEvidence> evidence) {
        public Result {
            evidence = List.copyOf(evidence);
        }
    }

    private final AssessmentPolicy policy;

    public ProblemAssessor(AssessmentPolicy policy) {
        this.policy = policy;
    }

    public Result assess(Map<String, String> fields) {
        Axis impact = axis("impact", fields);
        Axis urgency = axis("urgency", fields);
        var evidence = new ArrayList<AssessmentEvidence>();
        evidence.addAll(impact.evidence());
        evidence.addAll(urgency.evidence());
        var flags = new ArrayList<String>();
        policy.signals("risk")
                .forEach(
                        (flag, phrases) -> {
                            var spans =
                                    LiteralSignals.find(fields, phrases, "risk", "RISK-" + flag);
                            if (!spans.isEmpty()) {
                                flags.add(flag);
                                evidence.addAll(spans);
                            }
                        });
        // Draft rubric: flags are review hints; neither LOW nor another severity is inferred.
        var risk =
                new Risk(
                        flags.isEmpty() ? "NOT_ASSESSED" : "INSUFFICIENT_EVIDENCE",
                        null,
                        flags,
                        true);
        return new Result(
                impact, urgency, policy.priority(impact.value(), urgency.value()), risk, evidence);
    }

    private Axis axis(String slot, Map<String, String> fields) {
        var values = new ArrayList<String>();
        var evidence = new ArrayList<AssessmentEvidence>();
        policy.signals(slot)
                .forEach(
                        (value, phrases) -> {
                            var spans =
                                    LiteralSignals.find(
                                            fields,
                                            phrases,
                                            slot,
                                            slot.toUpperCase(java.util.Locale.ROOT) + "-" + value);
                            if (!spans.isEmpty()) {
                                values.add(value);
                                evidence.addAll(spans);
                            }
                        });
        return new Axis(
                values.size() == 1 ? values.get(0) : null,
                values.isEmpty()
                        ? "INSUFFICIENT_EVIDENCE"
                        : values.size() == 1 ? "SUPPORTED" : "CONFLICT",
                evidence);
    }
}
