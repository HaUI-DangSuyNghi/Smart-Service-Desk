package vn.haui.dx.apps.servicedesk.intelligence;

import java.util.List;
import vn.haui.dx.hpdi.intelligence.api.AssistanceOutcome;

/** Request-local advice. This projection grants no workflow or source authority. */
public record TicketIntelligenceView(
        String contractVersion,
        String requestId,
        String status,
        Triage triage,
        Risk risk,
        Guidance guidance,
        boolean reviewRequired,
        List<String> reviewReasons,
        List<String> nextQuestions,
        Runtime runtime) {

    public TicketIntelligenceView {
        reviewReasons = List.copyOf(reviewReasons);
        nextQuestions = List.copyOf(nextQuestions);
    }

    public record Triage(
            String status,
            String category,
            String priority,
            String suggestedGroup,
            String impact,
            String urgency,
            String reasonCode,
            List<String> matchedRules,
            List<InputEvidence> inputEvidence,
            boolean needsReview,
            Explanation explanation) {

        public Triage {
            matchedRules = List.copyOf(matchedRules);
            inputEvidence = List.copyOf(inputEvidence);
        }
    }

    public record InputEvidence(String field, String text) {}

    public record Explanation(String classificationReason, String priorityReason) {}

    public record Risk(String status, String level, String reasonCode) {}

    public record Guidance(
            String status,
            String answer,
            List<AssistanceOutcome.Citation> citations,
            String reasonCode) {

        public Guidance {
            citations = List.copyOf(citations);
        }
    }

    public record Runtime(String triageProfileVersion, String assistantPolicyVersion) {}
}
