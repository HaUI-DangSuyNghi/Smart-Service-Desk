package vn.haui.dx.apps.servicedesk.intelligence;

import java.util.List;

public record StoredAssessment(String status, int version, Result result, String errorCode) {
    public record Result(String contractVersion, String profileVersion, String engine, String rulesVersion,
            String modelVersion, String category, String priority, String impact, String urgency,
            String reasonCode, List<TicketIntelligenceView.InputEvidence> evidence,
            TicketIntelligenceView.Explanation explanation, boolean needsReview, List<String> nextQuestions) {}
}
