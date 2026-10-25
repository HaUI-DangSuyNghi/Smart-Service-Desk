package vn.haui.dx.hpdi.intelligence.api;

import java.util.List;

/** Public-safe citations contain request-local aliases and display text only. */
public record AssistanceOutcome(
        String contractVersion,
        String requestId,
        String status,
        String answer,
        List<Citation> citations,
        boolean needsReview,
        String reasonCode) {

    public AssistanceOutcome {
        citations = List.copyOf(citations);
    }

    public record Citation(String citationId, String displayTitle, String sourceVersion) {}
}
