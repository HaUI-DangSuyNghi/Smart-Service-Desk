package vn.haui.dx.core.audit.api;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** Một bản ghi audit, chỉ ghi thêm. */
public record AuditEntry(UUID id, Instant occurredAt, String actor, String action, String resourceType, String resource,
                         String workspace, String requestId, Map<String, Object> details) {
    public AuditEntry { details = Map.copyOf(details); }
}
