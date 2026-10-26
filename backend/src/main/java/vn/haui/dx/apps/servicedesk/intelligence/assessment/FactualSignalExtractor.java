package vn.haui.dx.apps.servicedesk.intelligence.assessment;

import com.fasterxml.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/** Parses quantities and relative deadlines; every conclusion retains its original literal span. */
public final class FactualSignalExtractor {
    public record Fact(String slot, String value, AssessmentEvidence evidence) {}

    public record Signal(String axis, String level, AssessmentEvidence evidence, List<Fact> facts) {
        public Signal {
            facts = List.copyOf(facts);
        }
    }

    private final JsonNode configuration;
    private final Pattern quantities;
    private final Pattern failures;
    private final Pattern wholeRooms;
    private final Pattern deadlines;
    private final Pattern events;
    private final Pattern exclusions;
    private static final Pattern CLAUSE_END =
            Pattern.compile("[;!?\\n]|(?<![0-9])\\.|\\.(?![0-9])");

    public FactualSignalExtractor(JsonNode configuration) {
        this.configuration = configuration.deepCopy();
        if (!"servicedesk-factual-signals@0.1.0".equals(configuration.path("version").asText())
                || !"DRAFT".equals(configuration.path("approvalStatus").asText())
                || !"U4".equals(configuration.path("imminentLevel").asText())
                || !"I3".equals(configuration.path("wholeRoomLevel").asText())) {
            throw new IllegalArgumentException("Unsupported factual extraction policy");
        }
        quantities = pattern("quantityPattern");
        failures = pattern("failurePattern");
        wholeRooms = pattern("wholeRoomPattern");
        deadlines = pattern("relativeTimePattern");
        events = pattern("eventPattern");
        exclusions = pattern("nonFactualPattern");
        if (!configuration.path("imminentWindowMinutes").canConvertToInt()
                || configuration.path("imminentWindowMinutes").asInt() < 1
                || configuration.path("imminentWindowMinutes").asInt() > 60
                || configuration.path("maxRelationChars").asInt() < 1
                || configuration.path("maxRelationChars").asInt() > 200) {
            throw new IllegalArgumentException("Invalid structured extraction budget");
        }
    }

    public List<Signal> extract(Map<String, String> fields) {
        var result = new ArrayList<Signal>();
        for (String field : List.of("title", "description")) {
            String source = fields.getOrDefault(field, "");
            var delimiters = CLAUSE_END.matcher(source);
            int start = 0;
            while (delimiters.find()) {
                extractClause(field, source, start, delimiters.start(), result);
                start = delimiters.end();
            }
            extractClause(field, source, start, source.length(), result);
        }
        return List.copyOf(result);
    }

    public boolean isObserved(String source, AssessmentEvidence evidence) {
        int start = source.offsetByCodePoints(0, evidence.start());
        int end = source.offsetByCodePoints(0, evidence.end());
        int left = 0, right = source.length();
        var boundaries = CLAUSE_END.matcher(source);
        while (boundaries.find()) {
            if (boundaries.end() <= start) {
                left = boundaries.end();
            } else if (boundaries.start() >= end) {
                right = boundaries.start();
                break;
            }
        }
        return !exclusions.matcher(source.substring(left, right)).find();
    }

    private void extractClause(
            String field, String source, int start, int end, List<Signal> result) {
        String clause = source.substring(start, end);
        if (exclusions.matcher(clause).find()) {
            return;
        }
        extractCounts(field, source, start, clause, result);
        extractDeadlines(field, source, start, clause, result);
    }

    private void extractCounts(
            String field, String source, int offset, String clause, List<Signal> result) {
        var failure = failures.matcher(clause);
        while (failure.find()) {
            String prefix = clause.substring(0, failure.start());
            if (prefix.matches("(?isu).*(?:không(?: hề| bị| có)?|not|no)\\s*$")) {
                continue;
            }
            var whole = wholeRooms.matcher(prefix);
            int wholeStart = -1, wholeEnd = -1;
            while (whole.find()) {
                wholeStart = whole.start();
                wholeEnd = whole.end();
            }
            var quantity = quantities.matcher(prefix);
            String count = null, unit = null;
            int quantityStart = -1;
            while (quantity.find()) {
                count = quantity.group("count");
                unit = quantity.group("unit");
                quantityStart = quantity.start();
            }
            if (quantityStart >= 0
                    && prefix.substring(0, quantityStart)
                            .matches(
                                    "(?isu).*(?:khoảng|tầm|xấp xỉ|ít nhất|tối đa|at least|at"
                                            + " most|about|around)\\s*$")) {
                continue;
            }
            int start = Math.max(wholeStart, quantityStart);
            if (start < 0
                    || failure.start() - start > configuration.path("maxRelationChars").asInt()) {
                continue;
            }
            var span =
                    span(
                            field,
                            source,
                            offset + start,
                            offset + failure.end(),
                            "impact",
                            "FACT-AFFECTED-SCOPE");
            var facts = new ArrayList<Fact>();
            String level;
            if (wholeStart >= 0 && wholeEnd > quantityStart) {
                level = configuration.path("wholeRoomLevel").asText();
                span =
                        span(
                                field,
                                source,
                                offset + wholeStart,
                                offset + failure.end(),
                                "impact",
                                "FACT-WHOLE-ROOM");
            } else {
                int amount = number(count);
                if (amount <= 0) {
                    continue;
                }
                String kind = configuration.path("quantityUnits").path(normalize(unit)).asText();
                var selected =
                        configuration
                                .path("countLevels")
                                .path(kind)
                                .path(amount == 1 ? "one" : "multiple");
                if (!selected.isTextual()) {
                    continue;
                }
                level = selected.asText();
                facts.add(new Fact("affectedCount", Integer.toString(amount), span));
            }
            result.add(new Signal("impact", level, span, facts));
        }
    }

    private void extractDeadlines(
            String field, String source, int offset, String clause, List<Signal> result) {
        var relative = deadlines.matcher(clause);
        while (relative.find()) {
            if (!events.matcher(clause.substring(0, relative.start())).find()) {
                continue;
            }
            BigDecimal amount;
            try {
                amount = new BigDecimal(relative.group("amount").replace(',', '.'));
            } catch (NumberFormatException invalid) {
                continue;
            }
            var multiplier =
                    configuration.path("timeUnitMinutes").path(normalize(relative.group("unit")));
            if (!multiplier.isNumber()) {
                continue;
            }
            BigDecimal minutes = amount.multiply(multiplier.decimalValue());
            if (minutes.signum() < 0
                    || minutes.compareTo(
                                    BigDecimal.valueOf(
                                            configuration.path("imminentWindowMinutes").asInt()))
                            > 0) {
                continue;
            }
            var span =
                    span(
                            field,
                            source,
                            offset,
                            offset + clause.length(),
                            "urgency",
                            "FACT-IMMINENT-EVENT");
            result.add(
                    new Signal(
                            "urgency",
                            configuration.path("imminentLevel").asText(),
                            span,
                            List.of(
                                    new Fact(
                                            "deadlineMinutes",
                                            minutes.stripTrailingZeros().toPlainString(),
                                            span))));
        }
    }

    private int number(String text) {
        if (text.matches("[0-9]+")) {
            return Integer.parseInt(text);
        }
        return configuration.path("countWords").path(normalize(text)).asInt(-1);
    }

    private static String normalize(String text) {
        return text.toLowerCase(java.util.Locale.ROOT).replaceAll("\\s+", " ");
    }

    private static AssessmentEvidence span(
            String field, String source, int start, int end, String axis, String rule) {
        var evidence =
                new AssessmentEvidence(
                        field,
                        source.substring(start, end),
                        source.codePointCount(0, start),
                        source.codePointCount(0, end),
                        List.of(axis),
                        "LITERAL_SPAN",
                        rule);
        if (!evidence.verifies(source)) {
            throw new IllegalStateException("Invalid extracted evidence");
        }
        return evidence;
    }

    private Pattern pattern(String key) {
        String expression = configuration.path(key).asText();
        if (expression.isBlank() || expression.length() > 2000) {
            throw new IllegalArgumentException("Invalid extraction grammar");
        }
        return Pattern.compile(expression, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    }
}
