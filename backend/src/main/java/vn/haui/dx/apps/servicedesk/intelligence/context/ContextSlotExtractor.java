package vn.haui.dx.apps.servicedesk.intelligence.context;

import org.springframework.stereotype.Component;

import vn.haui.dx.apps.servicedesk.intelligence.assessment.AssessmentPolicy;
import vn.haui.dx.apps.servicedesk.intelligence.assessment.FactualSignalExtractor;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/** CPU-only, không ghép nguồn và không trao quyền thực thi cho nội dung ticket. */
@Component
public final class ContextSlotExtractor {
    private record Fact(Object value, ContextProjection.Evidence evidence, boolean tentative) {}

    private final ContextPolicyCatalog policy;
    private final FactualSignalExtractor factual;
    private final Pattern nonFactual;
    private final Pattern tentative;
    private final Pattern correction;
    private final Pattern negatedEntity;
    private final Pattern negatedFailure;
    private final Pattern resolved;
    private final Pattern hypothetical;
    private final Pattern singleAffected;
    private static final Pattern CLAUSE_END = Pattern.compile("[;!?\\n]|(?<![0-9])\\.|\\.(?![0-9])");

    public ContextSlotExtractor(ContextPolicyCatalog policy, AssessmentPolicy assessment) {
        this.policy = policy;
        factual = new FactualSignalExtractor(assessment.structuredExtraction());
        nonFactual = policy.grammar("nonFactual");
        tentative = policy.grammar("tentative");
        correction = policy.grammar("correction");
        negatedEntity = policy.grammar("negatedEntity");
        negatedFailure = policy.grammar("negatedFailure");
        resolved = policy.grammar("resolved");
        hypothetical = policy.grammar("hypothetical");
        singleAffected = policy.grammar("singleAffected");
    }

    public ContextProjection extract(ContextSnapshot snapshot) {
        var facts = new LinkedHashMap<String, List<Fact>>();
        for (var source : snapshot.sources()) {
            extractSource(source, facts);
        }

        var result = new LinkedHashMap<String, ContextProjection.Slot>();
        for (String slot : policy.answerSlots()) {
            result.put(slot, resolve(slot, facts.getOrDefault(slot, List.of())));
        }
        boolean exceeded = facts.values().stream().flatMap(List::stream)
                .anyMatch(fact -> "FACT_BUDGET_EXCEEDED".equals(fact.evidence().ruleId()));
        return new ContextProjection(result, exceeded
                ? List.of("USER_REPORTED_NOT_VERIFIED", "NO_MODEL_CALL", "FACT_BUDGET_EXCEEDED")
                : List.of("USER_REPORTED_NOT_VERIFIED", "NO_MODEL_CALL"));
    }

    private void extractSource(ContextSnapshot.Source source, Map<String, List<Fact>> facts) {
        String text = source.text();
        if (source.slot() != null && directAnswer(source, facts)) {
            return;
        }

        var boundaries = CLAUSE_END.matcher(text);
        int start = 0;
        while (boundaries.find()) {
            extractClause(source, start, boundaries.start(), facts);
            start = boundaries.end();
        }
        extractClause(source, start, text.length(), facts);
    }

    private void extractClause(
            ContextSnapshot.Source source, int start, int end, Map<String, List<Fact>> facts) {
        String clause = source.text().substring(start, end);
        if (clause.isBlank()) {
            return;
        }
        if (nonFactual.matcher(clause).find()) {
            if (resolved.matcher(clause).find()) {
                add(facts, "problemEventStatus", "RESOLVED", source, start, end, "EXPLICIT_RESOLUTION", false);
            } else if (hypothetical.matcher(clause).find()) {
                add(facts, "problemEventStatus", "HYPOTHETICAL", source, start, end, "HYPOTHETICAL", false);
            }
            return;
        }

        boolean uncertain = tentative.matcher(clause).find();
        for (var rule : policy.patterns().entrySet()) {
            var matcher = rule.getValue().matcher(clause);
            while (matcher.find()) {
                if (!negatedEntity.matcher(clause.substring(0, matcher.start())).find()) {
                    add(facts, rule.getKey(), normalize(matcher.group("value")), source,
                            start + matcher.start(), start + matcher.end(), "ENTITY", uncertain);
                }
            }
        }
        for (var rule : policy.devices().entrySet()) {
            var matcher = rule.getValue().matcher(clause);
            while (matcher.find()) {
                if (!negatedEntity.matcher(clause.substring(0, matcher.start())).find()) {
                    add(facts, "device.type", rule.getKey(), source,
                            start + matcher.start(), start + matcher.end(), "DEVICE_TYPE", uncertain);
                }
            }
        }
        for (var rule : policy.symptoms().entrySet()) {
            var matcher = rule.getValue().matcher(clause);
            while (matcher.find()) {
                if (negatedEntity.matcher(clause.substring(0, matcher.start())).find()
                        || negatedFailure.matcher(clause.substring(0, matcher.start())).find()) {
                    continue;
                }
                add(facts, "symptomCodes", rule.getKey(), source,
                        start + matcher.start(), start + matcher.end(), "SYMPTOM", uncertain);
                add(facts, "problemEventStatus", "ACTIVE", source,
                        start + matcher.start(), start + matcher.end(), "REPORTED_FAILURE", uncertain);
            }
        }

        var single = singleAffected.matcher(clause);
        if (single.find()) {
            add(facts, "affectedCount", 1, source, start + single.start(), start + single.end(),
                    "ONLY_ONE_AFFECTED", uncertain);
            add(facts, "affectedScope", "INDIVIDUAL", source, start + single.start(), start + single.end(),
                    "ONLY_ONE_AFFECTED", uncertain);
            return;
        }

        // Adapter tường minh: AI07 chỉ thấy một field title có giới hạn mỗi lần.
        // Offset sau đó được ánh xạ về đúng source; không sửa contract AI07.
        for (var signal : factual.extract(Map.of("title", clause))) {
            for (var fact : signal.facts()) {
                var span = fact.evidence();
                int left = start + clause.offsetByCodePoints(0, span.start());
                int right = start + clause.offsetByCodePoints(0, span.end());
                if ("affectedCount".equals(fact.slot())) {
                    int amount = Integer.parseInt(fact.value());
                    add(facts, "affectedCount", amount, source, left, right, span.ruleId(), uncertain);
                    add(facts, "affectedScope", amount == 1 ? "INDIVIDUAL" : "MULTIPLE", source,
                            left, right, span.ruleId(), uncertain);
                } else if ("deadlineMinutes".equals(fact.slot())) {
                    add(facts, "timeWindow", "RELATIVE_MINUTES:" + fact.value(), source,
                            left, right, span.ruleId(), uncertain);
                }
            }
            if ("FACT-WHOLE-ROOM".equals(signal.evidence().ruleId())) {
                add(facts, "affectedScope", "WHOLE_ROOM", source, start, end,
                        "REPORTED_WHOLE_ROOM", uncertain);
            }
        }
    }

    private boolean directAnswer(ContextSnapshot.Source source, Map<String, List<Fact>> facts) {
        String value = normalize(source.text().strip());
        Object parsed = switch (source.slot()) {
            case "location.room" -> value.matches("[A-Z]?[0-9]{2,6}(?:[.-][0-9A-Z]{1,6})?") ? value : null;
            case "location.building" -> value.matches("[A-Z][0-9]{1,3}") ? value : null;
            case "location.floor" -> value.matches("[0-9]{1,2}") ? value : null;
            case "device.identifier" -> value.matches("[0-9]{1,6}|[A-Z]{1,8}[-_][0-9]{1,8}") ? value : null;
            case "device.type" -> policy.devices().containsKey(value) ? value : null;
            case "device.scope" -> List.of("INDIVIDUAL", "SHARED").contains(value) ? value : null;
            case "workBlocked", "workaroundAvailable" -> policy.parseBoolean(value);
            case "affectedCount" -> value.matches("[1-9][0-9]{0,3}") ? Integer.valueOf(value) : null;
            case "affectedScope" -> List.of("INDIVIDUAL", "MULTIPLE", "WHOLE_ROOM").contains(value) ? value : null;
            case "problemEventStatus" -> List.of("ACTIVE", "RESOLVED", "HYPOTHETICAL").contains(value) ? value : null;
            case "symptomCodes" -> policy.symptoms().containsKey(value) ? value : null;
            // Chưa có danh mục dịch vụ/thời gian đã duyệt: giữ UNKNOWN thay vì tạo định danh tùy ý.
            default -> null;
        };
        if (parsed == null) {
            return false;
        }
        add(facts, source.slot(), parsed, source, 0, source.text().length(), "STRUCTURED_ANSWER", false);
        return true;
    }

    private void add(
            Map<String, List<Fact>> facts, String slot, Object value, ContextSnapshot.Source source,
            int start, int end, String rule, boolean uncertain) {
        if (source.slot() != null && !source.slot().equals(slot)) {
            return;
        }
        boolean corrected = source.revision() > 0 && !uncertain
                && (source.correction() || correction.matcher(source.text()).find());
        var evidence = new ContextProjection.Evidence(
                source.sourceId(), source.revision(), source.text().substring(start, end),
                source.text().codePointCount(0, start), source.text().codePointCount(0, end),
                uncertain ? "TENTATIVE_" + rule : rule, corrected);
        if (!evidence.verifies(source.text())) {
            throw new IllegalStateException("Context evidence does not match its source");
        }
        var values = facts.computeIfAbsent(slot, ignored -> new ArrayList<>());
        if (values.size() < 64) {
            values.add(new Fact(value, evidence, uncertain));
        } else if (values.size() == 64) {
            values.add(new Fact(null, new ContextProjection.Evidence(
                    evidence.sourceId(), evidence.sourceRevision(), evidence.quote(), evidence.start(), evidence.end(),
                    "FACT_BUDGET_EXCEEDED", false), true));
        }
    }

    private static ContextProjection.Slot resolve(String slot, List<Fact> facts) {
        var current = new LinkedHashSet<Object>();
        var evidence = new ArrayList<ContextProjection.Evidence>();
        boolean exceeded = facts.stream().anyMatch(fact -> "FACT_BUDGET_EXCEEDED".equals(fact.evidence().ruleId()));
        boolean ambiguous = false;
        String correctionSource = null;
        for (var fact : facts) {
            evidence.add(fact.evidence());
            if (fact.evidence().correction() && !fact.evidence().sourceId().equals(correctionSource)) {
                current.clear();
                ambiguous = false;
                correctionSource = fact.evidence().sourceId();
            }
            current.add(fact.value());
            ambiguous |= fact.tentative();
        }
        boolean multiValue = "symptomCodes".equals(slot);
        String status = exceeded ? "AMBIGUOUS" : current.isEmpty() ? "UNKNOWN"
                : current.size() > 1 && !multiValue ? "CONFLICT"
                : ambiguous ? "AMBIGUOUS" : "USER_REPORTED";
        Object value = List.of("UNKNOWN", "CONFLICT", "AMBIGUOUS").contains(status) ? null
                : multiValue ? List.copyOf(current) : current.iterator().next();
        return new ContextProjection.Slot(value, status, evidence, ContextPolicyCatalog.VERSION);
    }

    private static String normalize(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFC).toUpperCase(Locale.ROOT);
    }
}
