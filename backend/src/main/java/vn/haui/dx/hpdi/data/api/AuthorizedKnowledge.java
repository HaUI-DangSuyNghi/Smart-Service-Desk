package vn.haui.dx.hpdi.data.api;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
/**
 * Nội dung tri thức đã kiểm quyền và checksum. {@code content} là byte gốc (JSON: base64);
 * {@code text} là cùng nội dung giải UTF-8, cho giao diện hiển thị thẳng.
 */
public record AuthorizedKnowledge(UUID metadataId, String workspace, String title, String version,
                                  String checksum, String usageRights, String mediaType, byte[] content, String text) {
    public AuthorizedKnowledge { content = content.clone(); }
    /** Tạo từ byte gốc; tri thức chỉ nhận text/plain, text/markdown nên giải UTF-8 được. */
    public static AuthorizedKnowledge of(UUID metadataId, String workspace, String title, String version,
                                         String checksum, String usageRights, String mediaType, byte[] content) {
        return new AuthorizedKnowledge(metadataId, workspace, title, version, checksum, usageRights, mediaType,
            content, new String(content, StandardCharsets.UTF_8));
    }
    @Override public byte[] content() { return content.clone(); }
}
