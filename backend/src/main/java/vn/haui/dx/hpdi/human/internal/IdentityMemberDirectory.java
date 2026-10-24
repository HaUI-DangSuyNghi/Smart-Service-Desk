package vn.haui.dx.hpdi.human.internal;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import vn.haui.dx.core.common.CoreException;
import vn.haui.dx.core.common.ErrorCode;
import vn.haui.dx.core.identity.api.WorkspaceAccess;
import vn.haui.dx.core.identity.api.WorkspaceDirectory;
import vn.haui.dx.hpdi.human.api.MemberDirectory;
import vn.haui.dx.hpdi.human.api.WorkspaceContext;
import vn.haui.dx.hpdi.human.api.WorkspaceMember;
/**
 * Ghép danh bạ của Core với vai trò trong token. Lỗi quyền (CoreException) để Errors của Core xử lý;
 * chỉ lỗi danh bạ không phản hồi được hạ cấp ở những chỗ hiển thị phụ (tên workspace, tên người tạo).
 */
@Service
public class IdentityMemberDirectory implements MemberDirectory {
    private final WorkspaceAccess access;
    private final WorkspaceDirectory directory;
    public IdentityMemberDirectory(WorkspaceAccess access, WorkspaceDirectory directory) {
        this.access = access; this.directory = directory;
    }
    public List<WorkspaceContext> myWorkspaces() {
        // Vai trò lấy từ token đã ký, danh bạ chỉ góp tên hiển thị
        Map<String, TreeSet<String>> roles = new TreeMap<>();
        access.current().memberships().forEach(m ->
            roles.computeIfAbsent(m.workspace(), key -> new TreeSet<>()).add(m.role().value()));
        return roles.entrySet().stream()
            .map(entry -> new WorkspaceContext(entry.getKey(), nameOrCode(entry.getKey()), entry.getValue()))
            .toList();
    }
    public List<WorkspaceMember> members(String workspace) {
        return directory.members(workspace).stream()
            .map(m -> new WorkspaceMember(m.sub(), m.username(), m.fullName(),
                m.roles().stream().map(role -> role.value()).collect(Collectors.toSet())))
            .toList();
    }
    public Map<String, String> displayNames(String workspace) {
        try {
            return directory.members(workspace).stream().collect(Collectors.toUnmodifiableMap(
                m -> m.sub(), m -> m.fullName() != null ? m.fullName() : m.username()));
        } catch (CoreException error) {
            if (error.code() == ErrorCode.SERVICE_UNAVAILABLE) return Map.of();
            throw error;
        }
    }
    private String nameOrCode(String workspace) {
        try {
            return directory.displayName(workspace);
        } catch (CoreException error) {
            if (error.code() == ErrorCode.SERVICE_UNAVAILABLE) return workspace;
            throw error;
        }
    }
}
