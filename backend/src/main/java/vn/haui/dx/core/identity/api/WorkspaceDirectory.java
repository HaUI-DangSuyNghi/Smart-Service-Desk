package vn.haui.dx.core.identity.api;

import java.util.List;
import java.util.Map;

/**
 * Danh bạ workspace lấy từ Keycloak (đọc qua Admin API, không đọc DB Keycloak).
 * Membership and workspace roles come from the signed token ({@link WorkspaceAccess}).
 * Current group permission mappings also feed {@link WorkspacePermissions}; directory members never grant roles.
 * Người gọi phải là thành viên workspace; Keycloak không phản hồi → {@code SERVICE_UNAVAILABLE}.
 */
public interface WorkspaceDirectory {
    default java.util.Set<String> supportGroupMembers(String workspace, String group) {
        throw vn.haui.dx.core.common.CoreException.unavailable("Support group directory is not configured");
    }
    void refresh(String workspace);

    /** Thành viên kèm vai trò, sắp theo username. Workspace chưa có group trong Keycloak → danh sách rỗng. */
    List<DirectoryMember> members(String workspace);

    /** Thuộc tính {@code displayName} của group {@code /workspaces/{workspace}}; không có thì trả chính mã workspace. */
    String displayName(String workspace);

    /**
     * Vai trò → quyền nhỏ (role của client {@code dx-permissions}) gán cho group {@code /workspaces/{workspace}/{role}}.
     * Used for display and permission enforcement, intersected with the caller's signed workspace roles.
     */
    Map<String, List<DirectoryPermission>> rolePermissions(String workspace);

    /**
     * Email (Keycloak) theo subject, chỉ để gửi thông báo từ phía máy chủ (worker, không có người gọi);
     * subject không có email hoặc danh bạ không phản hồi thì bỏ qua. Mặc định không có email nào.
     */
    default Map<String, String> emails(java.util.Collection<String> subjects) { return Map.of(); }
}
