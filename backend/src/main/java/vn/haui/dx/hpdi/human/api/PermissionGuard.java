package vn.haui.dx.hpdi.human.api;

/**
 * Kiểm quyền nhỏ (Keycloak) của người gọi trong workspace trước khi chạy một API hpdi.
 * Vai trò lấy từ token; quyền của từng vai trò lấy từ Keycloak. Keycloak không phản hồi → SERVICE_UNAVAILABLE
 * (không cho qua khi không kiểm được).
 */
public interface PermissionGuard {
    /** Thiếu quyền → FORBIDDEN; không phải thành viên workspace → FORBIDDEN. */
    void require(String workspace, String permission);
}
