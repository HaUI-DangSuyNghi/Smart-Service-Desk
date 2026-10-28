package vn.haui.dx.apps.servicedesk;

public record TicketStatistics(String workspace, String visibility, long total, long awaitingHandling,
                               long awaitingConfirmation, long completed, long unclassified,
                               long awaitingReview, long assigned, long inProgress, long pending,
                               long reopened, long closed, long overdue, long intake, long following) {}
