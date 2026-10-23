package vn.haui.dx.hpdi.data.api;
import java.time.Instant;
import java.util.UUID;
/** Một dòng trong danh sách Knowledge Hub; {@code ownerName} null khi danh bạ không phản hồi. */
public record KnowledgeSummary(UUID id, String workspace, String title, String source, String version,
                               String usageRights, String owner, String ownerName, Instant createdAt) {}
