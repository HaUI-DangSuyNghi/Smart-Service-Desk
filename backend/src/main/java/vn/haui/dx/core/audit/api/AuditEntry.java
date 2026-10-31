package vn.haui.dx.core.audit.api;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Một bản ghi audit, chỉ ghi thêm. */
public record AuditEntry(UUID id, Instant occurredAt, String actor, String action, String resourceType, String resource,
                         String workspace, String requestId, Map<String, Object> details) {
    // details có thể chứa giá trị null (Map.copyOf không cho phép)
    public AuditEntry { details = Collections.unmodifiableMap(new LinkedHashMap<>(details)); }
}
