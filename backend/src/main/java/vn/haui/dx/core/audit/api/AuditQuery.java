package vn.haui.dx.core.audit.api;

import java.time.Instant;

/** Bộ lọc audit; {@code null} = không lọc. {@code from} tính cả, {@code to} không tính. */
public record AuditQuery(Instant from, Instant to, String action, String actor) {}
