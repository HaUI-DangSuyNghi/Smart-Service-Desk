package vn.haui.dx.core.identity.api;

import org.springframework.stereotype.Service;
import vn.haui.dx.core.common.CoreException;

@Service
public final class WorkspacePermissions {
    private final WorkspaceAccess access;
    private final WorkspaceDirectory directory;

    public WorkspacePermissions(WorkspaceAccess access, WorkspaceDirectory directory) {
        this.access = access;
        this.directory = directory;
    }

    public void require(String workspace, String permission) {
        access.requireMember(workspace);
        require(access.current(), directory, workspace, permission);
    }

    public static void require(CurrentUser user, WorkspaceDirectory directory, String workspace, String permission) {
        if (!user.isMember(workspace)) throw CoreException.forbidden("Workspace access denied");
        var byRole = directory.rolePermissions(workspace);
        boolean granted = user.memberships().stream()
            .filter(member -> member.workspace().equals(workspace))
            .flatMap(member -> byRole.getOrDefault(member.role().value(), java.util.List.of()).stream())
            .anyMatch(item -> item.name().equals(permission));
        if (!granted) throw CoreException.forbidden("Permission required: " + permission);
    }
}
