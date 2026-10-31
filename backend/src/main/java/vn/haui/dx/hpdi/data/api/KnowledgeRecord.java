package vn.haui.dx.hpdi.data.api;

import java.time.Instant;
import java.util.UUID;

/**
 * Provenance của một tài liệu cho trang chi tiết: nguồn, phiên bản, quyền sử dụng, checksum, người tạo.
 * {@code ownerName} null khi danh bạ không phản hồi; {@code fileId} null khi bản ghi không kèm file.
 */
public record KnowledgeRecord(UUID id, String workspace, String title, String source, String version,
                              String usageRights, String checksum, UUID fileId, String owner, String ownerName,
                              Instant createdAt) {}
