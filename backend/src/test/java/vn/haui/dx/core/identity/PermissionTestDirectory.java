package vn.haui.dx.core.identity;

import java.util.List;
import java.util.Map;
import vn.haui.dx.core.identity.api.DirectoryPermission;
import vn.haui.dx.core.identity.api.WorkspaceDirectory;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

public final class PermissionTestDirectory {
    private PermissionTestDirectory() {}

    public static WorkspaceDirectory allowed() {
        var directory = mock(WorkspaceDirectory.class);
        var worker = List.of("request:create", "task:handle", "document:view", "document:upload", "audit:view-own")
            .stream().map(name -> new DirectoryPermission(name, null)).toList();
        var coordinator = List.of("request:create", "request:view-all", "task:handle", "task:assign", "document:view",
            "document:upload", "member:view", "audit:view", "audit:view-own", "dashboard:view")
            .stream().map(name -> new DirectoryPermission(name, null)).toList();
        when(directory.rolePermissions(anyString())).thenReturn(Map.of("agent", worker, "coordinator", coordinator,
            "requester", List.of(new DirectoryPermission("request:create", null), new DirectoryPermission("audit:view-own", null))));
        return directory;
    }
}
