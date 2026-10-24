package vn.haui.dx.hpdi.human;

import java.util.List;
import org.junit.jupiter.api.Test;
import vn.haui.dx.core.common.CoreException;
import vn.haui.dx.core.common.ErrorCode;
import vn.haui.dx.hpdi.common.api.CapabilityException;
import vn.haui.dx.hpdi.human.api.MemberDirectory;
import vn.haui.dx.hpdi.human.api.WorkspacePermission;
import vn.haui.dx.hpdi.human.internal.KeycloakPermissionGuard;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class KeycloakPermissionGuardTest {
    private final MemberDirectory directory = mock(MemberDirectory.class);
    private final KeycloakPermissionGuard guard = new KeycloakPermissionGuard(directory);

    @Test void allowsWhenCallerHasPermissionAndForbidsOtherwise() {
        when(directory.myPermissions("lab-a")).thenReturn(List.of(new WorkspacePermission("request:create", "Tạo yêu cầu")));
        assertDoesNotThrow(() -> guard.require("lab-a", "request:create"));
        var error = assertThrows(CapabilityException.class, () -> guard.require("lab-a", "task:handle"));
        assertEquals(CapabilityException.Code.FORBIDDEN, error.code());
    }

    @Test void keycloakOutageIsNotTreatedAsGranted() {
        // Không kiểm được quyền thì báo 503, không cho qua
        when(directory.myPermissions("lab-a")).thenThrow(CoreException.unavailable("down"));
        var error = assertThrows(CoreException.class, () -> guard.require("lab-a", "request:create"));
        assertEquals(ErrorCode.SERVICE_UNAVAILABLE, error.code());
    }
}
