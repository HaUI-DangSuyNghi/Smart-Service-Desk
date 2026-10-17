package vn.haui.dx.apps.servicedesk;

public record TicketStatistics(String workspace, String visibility, long total, long awaitingHandling,
                               long awaitingConfirmation, long completed, long unclassified) {}
