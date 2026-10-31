package vn.haui.dx.core.data.api;

import java.io.InputStream;
import java.util.Set;
import java.util.UUID;

/**
 * Lưu file riêng theo workspace. Thành viên được upload. Đọc cần là thành viên và, nếu file có liên kết,
 * được ít nhất một {@link FileAccessPolicy} chấp nhận; không được đọc → NOT_FOUND.
 */
public interface FileService {
    long MAX_SIZE_BYTES = 5L * 1024 * 1024;
    Set<String> ALLOWED_MEDIA_TYPES = Set.of("application/pdf", "text/plain", "text/markdown", "image/png", "image/jpeg", "application/vnd.openxmlformats-officedocument.wordprocessingml.document");

    /** Lưu và tính SHA-256. Quá 5 MiB → PAYLOAD_TOO_LARGE; sai loại/đuôi/magic bytes → UNSUPPORTED_MEDIA_TYPE. */
    StoredFile store(String workspace, String originalName, String mediaType, InputStream content);
    StoredFile describe(String workspace, UUID fileId);
    /** Nơi gọi phải tự đóng stream. */
    InputStream open(String workspace, UUID fileId);
    /** Gắn file vào đối tượng của app; sau đó quyền đọc theo {@link FileAccessPolicy}. */
    void link(String workspace, UUID fileId, String resourceType, String resourceId);
}
