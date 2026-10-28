package vn.haui.dx.hpdi.human.internal;

import org.springframework.stereotype.Service;
import vn.haui.dx.core.identity.api.WorkspacePermissions;
import vn.haui.dx.hpdi.human.api.PermissionGuard;

/** Kiểm quyền bằng danh sách quyền của người gọi (đã cache 60 giây ở danh bạ Core). */
@Service
public class KeycloakPermissionGuard implements PermissionGuard {
    private final WorkspacePermissions permissions;
    private final vn.haui.dx.core.identity.api.WorkspaceAccess access;
    private final vn.haui.dx.core.identity.api.WorkspaceDirectory directory;

    public KeycloakPermissionGuard(WorkspacePermissions permissions, vn.haui.dx.core.identity.api.WorkspaceAccess access,
            vn.haui.dx.core.identity.api.WorkspaceDirectory directory) {
        this.permissions = permissions; this.access = access; this.directory = directory;
    }

    @Override public void requireCurrent(String workspace, String role, String permission) {
        access.requireMember(workspace);
        var user = access.current();
        directory.refresh(workspace);
        boolean current = directory.members(workspace).stream().anyMatch(member -> member.sub().equals(user.sub())
            && member.roles().stream().anyMatch(item -> role.equals(item.value()) && user.hasRole(workspace, item)));
        boolean granted = directory.rolePermissions(workspace).getOrDefault(role, java.util.List.of()).stream()
            .anyMatch(item -> permission.equals(item.name()));
        if (!current || !granted) throw vn.haui.dx.hpdi.common.api.CapabilityException.forbidden("Current role and permission required");
    }

    @Override
    public void require(String workspace, String permission) {
        // myPermissions tự kiểm thành viên workspace; danh bạ lỗi thì ném 503, không coi là có quyền
        permissions.require(workspace, permission);
    }
}
