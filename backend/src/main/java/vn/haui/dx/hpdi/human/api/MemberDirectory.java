package vn.haui.dx.hpdi.human.api;
import java.util.List;
import java.util.Map;
import java.util.Set;
/** Người dùng / nhóm của chữ H: workspace của tôi và thành viên từng workspace. */
public interface MemberDirectory {
    List<WorkspaceMember> currentAgents(String workspace, String supportGroup);
    /** Workspace của người gọi (theo token) kèm tên hiển thị; danh bạ lỗi thì tên = mã workspace. */
    List<WorkspaceContext> myWorkspaces();
    /** Thành viên workspace; người gọi phải là thành viên. Danh bạ lỗi → SERVICE_UNAVAILABLE. */
    List<WorkspaceMember> members(String workspace);
    /** subject → tên hiển thị (họ tên, không có thì username); danh bạ lỗi → map rỗng. */
    Map<String, String> displayNames(String workspace);
    /**
     * Quyền của người gọi trong workspace: gộp quyền của các vai trò người gọi có (vai trò lấy từ token),
     * sắp theo tên. Chỉ để hiển thị. Danh bạ lỗi → SERVICE_UNAVAILABLE.
     */
    List<WorkspacePermission> myPermissions(String workspace);
    /**
     * subject → tên hiển thị, chỉ cho các subject được hỏi (người tạo, người xử lý mà người gọi đã thấy ở trang khác).
     * Mọi thành viên workspace được gọi; danh bạ lỗi → map rỗng.
     */
    Map<String, String> names(String workspace, Set<String> subjects);
}
