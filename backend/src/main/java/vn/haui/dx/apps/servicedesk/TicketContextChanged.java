package vn.haui.dx.apps.servicedesk;

import java.util.UUID;

/** Sự kiện nội bộ được phát trong transaction ghi ticket hoặc câu trả lời. */
public record TicketContextChanged(String workspace, UUID ticketId, long contextRevision) {}
