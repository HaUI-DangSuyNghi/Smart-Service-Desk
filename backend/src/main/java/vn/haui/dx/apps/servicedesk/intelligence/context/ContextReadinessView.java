package vn.haui.dx.apps.servicedesk.intelligence.context;

import java.util.List;
import java.util.UUID;

/** Readiness chỉ mô tả phụ thuộc của tác vụ AI; không phải trạng thái Flowable. */
public record ContextReadinessView(
        String contractVersion, UUID ticketId, long contextRevision, String contextDigest,
        State overall, List<Task> tasks, List<String> reasonCodes, String nextAction,
        List<Question> questions, String policyDigest,
        boolean needsReview, boolean operationalAuthority, boolean workflowGateApplied) {

    public static final String VERSION = "ticket-readiness@0.1.0";

    public ContextReadinessView {
        tasks = List.copyOf(tasks);
        reasonCodes = List.copyOf(reasonCodes);
        questions = List.copyOf(questions);
    }

    public enum State {
        READINESS_OK, ENRICHMENT_OPTIONAL, ENRICHMENT_REQUIRED, MANUAL_REVIEW_REQUIRED
    }

    public record Task(String task, State state, List<String> missingSlots, List<String> blockedOperations) {
        public Task {
            missingSlots = List.copyOf(missingSlots);
            blockedOperations = List.copyOf(blockedOperations);
        }
    }

    public record Question(String questionId, String slot, String text, List<String> choices, boolean correctionRequired) {
        public Question { choices = List.copyOf(choices); }
    }
}
