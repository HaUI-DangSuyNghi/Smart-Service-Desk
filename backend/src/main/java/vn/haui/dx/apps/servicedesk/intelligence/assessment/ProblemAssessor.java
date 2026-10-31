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
            List<AssessmentEvidence> evidence,
            List<FactualSignalExtractor.Fact> facts) {
        public Result {
            evidence = List.copyOf(evidence);
            facts = List.copyOf(facts);
        }
    }

    private final AssessmentPolicy policy;
    private final FactualSignalExtractor extractor;

    @org.springframework.beans.factory.annotation.Autowired
    public ProblemAssessor(AssessmentPolicy policy) {
        this(policy, new FactualSignalExtractor(policy.structuredExtraction()));
    }

    public ProblemAssessor(AssessmentPolicy policy, FactualSignalExtractor extractor) {
        this.policy = policy;
        this.extractor = extractor;
    }

    public Result assess(Map<String, String> fields) {
        var extracted = extractor.extract(fields);
        Axis impact = axis("impact", fields, extracted);
        Axis urgency = axis("urgency", fields, extracted);
        var evidence = new ArrayList<AssessmentEvidence>();
        evidence.addAll(impact.evidence());
        evidence.addAll(urgency.evidence());
        var flags = new ArrayList<String>();
        policy.signals("risk")
                .forEach(
                        (flag, phrases) -> {
                            var spans =
                                    observed(
                                            LiteralSignals.find(
                                                    fields, phrases, "risk", "RISK-" + flag),
                                            fields);
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
                impact,
                urgency,
                policy.priority(impact.value(), urgency.value()),
                risk,
                evidence,
                extracted.stream().flatMap(signal -> signal.facts().stream()).toList());
    }

    private Axis axis(
            String slot,
            Map<String, String> fields,
            List<FactualSignalExtractor.Signal> extracted) {
        var values = new java.util.LinkedHashSet<String>();
        var evidence = new ArrayList<AssessmentEvidence>();
        policy.signals(slot)
                .forEach(
                        (value, phrases) -> {
                            var spans =
                                    observed(
                                            LiteralSignals.find(
                                                    fields,
                                                    phrases,
                                                    slot,
                                                    slot.toUpperCase(java.util.Locale.ROOT)
                                                            + "-"
                                                            + value),
                                            fields);
                            if (!spans.isEmpty()) {
                                values.add(value);
                                evidence.addAll(spans);
                            }
                        });
        extracted.stream()
                .filter(signal -> slot.equals(signal.axis()))
                .forEach(
                        signal -> {
                            values.add(signal.level());
                            evidence.removeIf(
                                    previous ->
                                            previous.field().equals(signal.evidence().field())
                                                    && previous.start() == signal.evidence().start()
                                                    && previous.end() == signal.evidence().end());
                            evidence.add(signal.evidence());
                        });
        return new Axis(
                values.size() == 1 ? values.iterator().next() : null,
                values.isEmpty()
                        ? "INSUFFICIENT_EVIDENCE"
                        : values.size() == 1 ? "SUPPORTED" : "CONFLICT",
                evidence);
    }

    private List<AssessmentEvidence> observed(
            List<AssessmentEvidence> spans, Map<String, String> fields) {
        return spans.stream()
                .filter(span -> extractor.isObserved(fields.get(span.field()), span))
                .toList();
    }
}
