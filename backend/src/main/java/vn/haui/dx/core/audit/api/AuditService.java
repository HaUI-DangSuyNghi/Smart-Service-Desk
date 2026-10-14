package vn.haui.dx.core.audit.api;

import java.util.Map;
import vn.haui.dx.core.common.Page;
import vn.haui.dx.core.common.PageRequest;

/** Nhật ký audit chỉ ghi thêm; không có sửa/xóa. */
public interface AuditService {
    /**
     * Ghi trong transaction của nơi gọi; actor, thời điểm, request ID do core tự lấy.
     * {@code action} dạng {@code <đối tượng>.<động từ>}, app thêm tiền tố riêng (ví dụ {@code servicedesk.ticket.create}).
     * {@code details} không chứa token, mật khẩu, nội dung file.
     */
    void record(String action, String resourceType, String resourceId, String workspace, Map<String, Object> details);

    /** Mới nhất trước. Chỉ coordinator của workspace và platform-admin; người khác → FORBIDDEN. */
    Page<AuditEntry> search(String workspace, AuditQuery query, PageRequest page);
}
