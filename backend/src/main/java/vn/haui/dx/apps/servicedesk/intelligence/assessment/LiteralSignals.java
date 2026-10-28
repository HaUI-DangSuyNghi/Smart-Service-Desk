package vn.haui.dx.apps.servicedesk.intelligence.assessment;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/** Deliberately conservative: literal symptom phrases, never bag-of-word votes. */
public final class LiteralSignals {
    private LiteralSignals() {}

    public static List<AssessmentEvidence> find(
            Map<String, String> fields, List<String> phrases, String slot, String ruleId) {
        var evidence = new ArrayList<AssessmentEvidence>();
        for (String name : List.of("title", "description")) {
            String text = fields.getOrDefault(name, "");
            for (String phrase : phrases) {
                var matcher =
                        Pattern.compile(
                                        "(?<![\\p{L}\\p{N}])"
                                                + Pattern.quote(phrase)
                                                + "(?![\\p{L}\\p{N}])",
                                        Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE)
                                .matcher(text);
                while (matcher.find() && evidence.size() < 64) {
                    String prefix =
                            text.substring(Math.max(0, matcher.start() - 40), matcher.start())
                                    .toLowerCase(Locale.ROOT);
                    if (prefix.matches(
                            "(?s).*(không phải|không có|not|no|ví dụ|example|giả"
                                + " sử|nếu|if)\\s*$")) {
                        continue;
                    }
                    var span =
                            new AssessmentEvidence(
                                    name,
                                    matcher.group(),
                                    text.codePointCount(0, matcher.start()),
                                    text.codePointCount(0, matcher.end()),
                                    List.of(slot),
                                    "LITERAL_SPAN",
                                    ruleId);
                    if (!span.verifies(text)) {
                        throw new IllegalStateException("Invalid literal evidence");
                    }
                    if (!evidence.contains(span)) {
                        evidence.add(span);
                    }
                }
            }
        }
        return List.copyOf(evidence);
    }
}
