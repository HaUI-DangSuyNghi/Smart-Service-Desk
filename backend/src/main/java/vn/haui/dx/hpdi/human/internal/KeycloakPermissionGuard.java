package vn.haui.dx.hpdi.human.internal;

import org.springframework.stereotype.Service;
import vn.haui.dx.core.identity.api.WorkspacePermissions;
import vn.haui.dx.hpdi.human.api.PermissionGuard;

/** Kiểm quyền bằng danh sách quyền của người gọi (đã cache 60 giây ở danh bạ Core). */
@Service
public class KeycloakPermissionGuard implements PermissionGuard {
    private final WorkspacePermissions permissions;

    public KeycloakPermissionGuard(WorkspacePermissions permissions) { this.permissions = permissions; }

    @Override
    public void require(String workspace, String permission) {
        // myPermissions tự kiểm thành viên workspace; danh bạ lỗi thì ném 503, không coi là có quyền
        permissions.require(workspace, permission);
    }
}
