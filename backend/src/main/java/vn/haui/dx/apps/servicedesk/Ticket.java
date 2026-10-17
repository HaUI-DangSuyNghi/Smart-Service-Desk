package vn.haui.dx.apps.servicedesk;

import java.time.Instant;
import java.util.UUID;
import vn.haui.dx.core.workflow.api.ProcessRef;

public record Ticket(UUID id, String workspace, String creator, UUID clientRequestId,
                     String title, String description, Instant createdAt, ProcessRef process) {}
