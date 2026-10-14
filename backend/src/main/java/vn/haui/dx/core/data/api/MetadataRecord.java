package vn.haui.dx.core.data.api;

import java.time.Instant;
import java.util.UUID;

/** Metadata đã lưu; không sửa sau khi tạo, phiên bản mới là bản ghi mới. */
public record MetadataRecord(UUID id, String workspace, String title, String source, String version, String usageRights,
                             String checksum, UUID fileId, String owner, Instant createdAt) {}
