package vn.haui.dx.core.data.api;

import java.time.Instant;
import java.util.UUID;

/** Thông tin file đã lưu. {@code originalName} đã làm sạch, chỉ dùng cho header khi tải về. */
public record StoredFile(UUID id, String workspace, String originalName, String mediaType, long size, String checksum,
                         String owner, Instant createdAt) {}
