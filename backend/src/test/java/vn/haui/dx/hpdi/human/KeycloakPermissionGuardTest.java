package vn.haui.dx.hpdi.human;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import vn.haui.dx.core.common.CoreException;
import vn.haui.dx.core.common.ErrorCode;
import vn.haui.dx.core.identity.api.*;
import vn.haui.dx.hpdi.human.internal.KeycloakPermissionGuard;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class KeycloakPermissionGuardTest {
    private final WorkspaceDirectory directory = mock(WorkspaceDirectory.class);
    private final WorkspaceAccess access = mock(WorkspaceAccess.class);
    private final CurrentUser user = mock(CurrentUser.class);
    private final KeycloakPermissionGuard guard = new KeycloakPermissionGuard(new WorkspacePermissions(access, directory));

    @BeforeEach void setup() {
        when(access.current()).thenReturn(user);
        when(user.isMember("lab-a")).thenReturn(true);
        when(user.memberships()).thenReturn(Set.of(new Membership("lab-a", WorkspaceRole.REQUESTER)));
    }

    @Test void allowsWhenCallerHasPermissionAndForbidsOtherwise() {
        when(directory.rolePermissions("lab-a")).thenReturn(Map.of("requester", List.of(new DirectoryPermission("request:create", null))));
        assertDoesNotThrow(() -> guard.require("lab-a", "request:create"));
        var error = assertThrows(CoreException.class, () -> guard.require("lab-a", "task:handle"));
        assertEquals(ErrorCode.FORBIDDEN, error.code());
    }

    @Test void keycloakOutageIsNotTreatedAsGranted() {
        when(directory.rolePermissions("lab-a")).thenThrow(CoreException.unavailable("down"));
        var error = assertThrows(CoreException.class, () -> guard.require("lab-a", "request:create"));
        assertEquals(ErrorCode.SERVICE_UNAVAILABLE, error.code());
    }

    @Test void changedPolicyIsObservedOnTheNextGuardCall() {
        when(directory.rolePermissions("lab-a")).thenReturn(Map.of("requester", List.of(new DirectoryPermission("request:create", null))), Map.of());
        assertDoesNotThrow(() -> guard.require("lab-a", "request:create"));
        assertEquals(ErrorCode.FORBIDDEN, assertThrows(CoreException.class, () -> guard.require("lab-a", "request:create")).code());
    }

    @Test void allKnowledgeReadFormsUseTheSameRevocablePolicyAsCore() {
        var catalog = mock(vn.haui.dx.hpdi.data.api.KnowledgeCatalog.class);
        var controller = new vn.haui.dx.hpdi.data.web.KnowledgeController(catalog, guard);
        var id = java.util.UUID.randomUUID();
        assertEquals(ErrorCode.FORBIDDEN, assertThrows(CoreException.class,
            () -> controller.search("lab-a", null, 0, 20)).code());
        assertEquals(ErrorCode.FORBIDDEN, assertThrows(CoreException.class,
            () -> controller.resolve("lab-a", id)).code());
        assertEquals(ErrorCode.FORBIDDEN, assertThrows(CoreException.class,
            () -> controller.record("lab-a", id)).code());
        verifyNoInteractions(catalog);
    }
}
