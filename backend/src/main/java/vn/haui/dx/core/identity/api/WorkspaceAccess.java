package vn.haui.dx.core.identity.api;

/**
 * Kiểm quyền cho request hiện tại. Workspace luôn lấy từ URL, đối chiếu với token đã ký.
 * Không đủ quyền → {@code CoreException(FORBIDDEN)}.
 */
public interface WorkspaceAccess {
    CurrentUser current();
    void requireMember(String workspace);
    void requireRole(String workspace, WorkspaceRole... anyOf);
}
