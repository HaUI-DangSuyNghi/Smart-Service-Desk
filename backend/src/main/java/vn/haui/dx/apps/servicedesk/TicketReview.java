package vn.haui.dx.apps.servicedesk;

import jakarta.validation.constraints.*;
import vn.haui.dx.hpdi.process.api.QueueItem;

public final class TicketReview {
    private TicketReview() {}
    public enum Category { HARDWARE, NETWORK, SOFTWARE, ACCOUNT, OTHER }
    public enum Priority { LOW, MEDIUM, HIGH, CRITICAL }
    public record Decision(@NotNull Category category, @NotNull Priority priority,
                           @Min(1) @Max(10080) Integer estimateMinutes,
                           @com.fasterxml.jackson.annotation.JsonProperty(required=true) @Size(max=100) String assignee,
                           @NotBlank @Size(max=1000) String reason,
                           @Pattern(regexp="I[1-4]") String impact, @Pattern(regexp="U[1-4]") String urgency,
                           Long expectedContextRevision, Long expectedIntakeVersion) {
        public Decision(Category category, Priority priority, Integer estimateMinutes, String assignee,
                String reason, String impact, String urgency) {
            this(category, priority, estimateMinutes, assignee, reason, impact, urgency, null, null);
        }
    }
    public record View(@com.fasterxml.jackson.annotation.JsonUnwrapped Ticket ticket, String state,
                       String taskId, String assignee, QueueItem.Estimate estimate,
                       String workState, Long workedMinutes, Long remainingMinutes, int reopenCount, Classification classification,
                       vn.haui.dx.apps.servicedesk.intelligence.StoredAssessment assessment,
                       vn.haui.dx.hpdi.process.api.Sla sla,
                       @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
                       java.util.List<TicketCorrection.History> corrections,
                       @com.fasterxml.jackson.annotation.JsonUnwrapped TicketConfirmation.State confirmation) {}
    public record Classification(String category, String priority, String basePriority, boolean escalated,
                                 String group, String source, String impact, String urgency,
                                 String reviewedBy, java.time.Instant reviewedAt) {}
    public static int defaultMinutes(Category category, Priority priority) {
        return switch (category) {
            case HARDWARE -> switch (priority) { case LOW -> 45; case MEDIUM -> 60; case HIGH -> 90; case CRITICAL -> 120; };
            case NETWORK, OTHER -> switch (priority) { case LOW -> 30; case MEDIUM -> 45; case HIGH -> 60; case CRITICAL -> 90; };
            case SOFTWARE -> switch (priority) { case LOW, MEDIUM -> 30; case HIGH -> 45; case CRITICAL -> 60; };
            case ACCOUNT -> switch (priority) { case LOW, MEDIUM -> 15; case HIGH -> 20; case CRITICAL -> 30; };
        };
    }
}
