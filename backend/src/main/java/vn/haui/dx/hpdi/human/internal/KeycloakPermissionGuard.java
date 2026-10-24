package vn.haui.dx.hpdi.human.internal;

import org.springframework.stereotype.Service;
import vn.haui.dx.hpdi.common.api.CapabilityException;
import vn.haui.dx.hpdi.human.api.MemberDirectory;
import vn.haui.dx.hpdi.human.api.PermissionGuard;

/** Kiểm quyền bằng danh sách quyền của người gọi (đã cache 60 giây ở danh bạ Core). */
@Service
public class KeycloakPermissionGuard implements PermissionGuard {
    private final MemberDirectory directory;

    public KeycloakPermissionGuard(MemberDirectory directory) { this.directory = directory; }

    @Override
    public void require(String workspace, String permission) {
        // myPermissions tự kiểm thành viên workspace; danh bạ lỗi thì ném 503, không coi là có quyền
        boolean granted = directory.myPermissions(workspace).stream()
            .anyMatch(item -> item.key().equals(permission));
        if (!granted) throw CapabilityException.forbidden("Permission required: " + permission);
    }
}
