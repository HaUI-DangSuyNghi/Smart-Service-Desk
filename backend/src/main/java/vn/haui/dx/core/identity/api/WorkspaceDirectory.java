package vn.haui.dx.core.identity.api;

import java.util.List;

/**
 * Danh bạ workspace lấy từ Keycloak (đọc qua Admin API, không đọc DB Keycloak).
 * Chỉ để hiển thị: quyền vẫn lấy từ token ({@link WorkspaceAccess}), không lấy từ danh bạ.
 * Người gọi phải là thành viên workspace; Keycloak không phản hồi → {@code SERVICE_UNAVAILABLE}.
 */
public interface WorkspaceDirectory {
    /** Thành viên kèm vai trò, sắp theo username. Workspace chưa có group trong Keycloak → danh sách rỗng. */
    List<DirectoryMember> members(String workspace);

    /** Thuộc tính {@code displayName} của group {@code /workspaces/{workspace}}; không có thì trả chính mã workspace. */
    String displayName(String workspace);
}
