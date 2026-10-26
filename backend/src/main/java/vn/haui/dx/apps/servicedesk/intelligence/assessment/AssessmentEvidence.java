package vn.haui.dx.apps.servicedesk.intelligence.assessment;

import java.util.List;

/** Offsets count Unicode code points in the original persisted field. */
public record AssessmentEvidence(
        String field,
        String quote,
        int start,
        int end,
        List<String> supports,
        String verification,
        String ruleId) {
    public AssessmentEvidence {
        supports = List.copyOf(supports);
    }

    public boolean verifies(String source) {
        if (source == null
                || start < 0
                || end <= start
                || end > source.codePointCount(0, source.length())) {
            return false;
        }
        return "LITERAL_SPAN".equals(verification)
                && quote.equals(
                        source.substring(
                                source.offsetByCodePoints(0, start),
                                source.offsetByCodePoints(0, end)));
    }
}
