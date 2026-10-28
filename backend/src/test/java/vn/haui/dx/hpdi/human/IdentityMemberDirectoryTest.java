package vn.haui.dx.hpdi.human;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import vn.haui.dx.core.common.CoreException;
import vn.haui.dx.core.identity.api.CurrentUser;
import vn.haui.dx.core.identity.api.DirectoryMember;
import vn.haui.dx.core.identity.api.DirectoryPermission;
import vn.haui.dx.core.identity.api.Membership;
import vn.haui.dx.core.identity.api.WorkspaceAccess;
import vn.haui.dx.core.identity.api.WorkspaceDirectory;
import vn.haui.dx.core.identity.api.WorkspaceRole;
import vn.haui.dx.hpdi.human.api.WorkspaceContext;
import vn.haui.dx.hpdi.human.api.WorkspacePermission;
import vn.haui.dx.hpdi.human.internal.IdentityMemberDirectory;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class IdentityMemberDirectoryTest {
    @Test void legacyMemberDirectoryContractRemainsUnchangedAndMissingSupportProviderFailsClosed() {
        assertEquals(Set.of("myWorkspaces", "members", "displayNames", "myPermissions", "names"),
            java.util.Arrays.stream(vn.haui.dx.hpdi.human.api.MemberDirectory.class.getDeclaredMethods())
                .map(java.lang.reflect.Method::getName).collect(java.util.stream.Collectors.toSet()));
        var legacy = mock(WorkspaceDirectory.class, CALLS_REAL_METHODS);
        var error = assertThrows(CoreException.class, () -> legacy.supportGroupMembers("lab-a", "hardware"));
        assertEquals(vn.haui.dx.core.common.ErrorCode.SERVICE_UNAVAILABLE, error.code());
    }
    @Test void supportMembershipCannotGrantAgentRoleOrRestoreRevokedPermission() {
        var access = mock(WorkspaceAccess.class);
        var directory = mock(WorkspaceDirectory.class);
        var members = new IdentityMemberDirectory(access, directory);
        when(directory.members("lab-a")).thenReturn(List.of(
            new DirectoryMember("coordinator", "coordinator", null, Set.of(WorkspaceRole.COORDINATOR)),
            new DirectoryMember("agent", "agent", null, Set.of(WorkspaceRole.AGENT))));
        when(directory.supportGroupMembers("lab-a", "hardware")).thenReturn(Set.of("coordinator", "agent", "outsider"));
        when(directory.rolePermissions("lab-a")).thenReturn(Map.of("agent", List.of(new DirectoryPermission("task:handle", null))));
        assertEquals(List.of("agent"), members.currentAgents("lab-a", "hardware").stream().map(member -> member.subject()).toList());
        when(directory.rolePermissions("lab-a")).thenReturn(Map.of());
        assertTrue(members.currentAgents("lab-a", "hardware").isEmpty());
        verify(directory, times(2)).refresh("lab-a");
    }
    private final WorkspaceAccess access = mock(WorkspaceAccess.class);
    private final WorkspaceDirectory directory = mock(WorkspaceDirectory.class);
    private final IdentityMemberDirectory members = new IdentityMemberDirectory(access, directory);

    @Test void myWorkspacesTakeRolesFromTokenAndNamesFromDirectory() {
        var user = mock(CurrentUser.class);
        when(user.memberships()).thenReturn(Set.of(
            new Membership("lab-b", WorkspaceRole.REQUESTER),
            new Membership("lab-a", WorkspaceRole.AGENT),
            new Membership("lab-a", WorkspaceRole.COORDINATOR)));
        when(access.current()).thenReturn(user);
        when(directory.displayName("lab-a")).thenReturn("Phòng máy A");
        // Danh bạ lỗi chỉ làm mất tên hiển thị, không làm hỏng danh sách workspace
        when(directory.displayName("lab-b")).thenThrow(CoreException.unavailable("down"));

        assertEquals(List.of(
            new WorkspaceContext("lab-a", "Phòng máy A", Set.of("agent", "coordinator")),
            new WorkspaceContext("lab-b", "lab-b", Set.of("requester"))), members.myWorkspaces());
    }

    @Test void displayNamesPreferFullNameAndDegradeToEmptyWhenUnavailable() {
        when(directory.members("lab-a")).thenReturn(List.of(
            new DirectoryMember("s1", "user1", "Demo Requester", Set.of(WorkspaceRole.REQUESTER)),
            new DirectoryMember("s9", "superadmin", null, Set.of(WorkspaceRole.AGENT))));
        assertEquals(Map.of("s1", "Demo Requester", "s9", "superadmin"), members.displayNames("lab-a"));

        when(directory.members("lab-b")).thenThrow(CoreException.unavailable("down"));
        assertEquals(Map.of(), members.displayNames("lab-b"));
    }

    @Test void myPermissionsMergeRolesFromTokenOnlyInThatWorkspace() {
        var user = mock(CurrentUser.class);
        when(user.memberships()).thenReturn(Set.of(
            new Membership("lab-a", WorkspaceRole.REQUESTER),
            new Membership("lab-a", WorkspaceRole.AGENT),
            new Membership("lab-b", WorkspaceRole.COORDINATOR)));
        when(access.current()).thenReturn(user);
        when(directory.rolePermissions("lab-a")).thenReturn(Map.of(
            "requester", List.of(new DirectoryPermission("request:create", "Tạo yêu cầu")),
            "agent", List.of(new DirectoryPermission("request:create", "Tạo yêu cầu"),
                new DirectoryPermission("document:upload", null)),
            "coordinator", List.of(new DirectoryPermission("task:assign", "Giao việc cho người khác"))));

        // Trùng quyền giữa hai vai trò chỉ giữ một; vai trò coordinator ở lab-b không tính cho lab-a;
        // thiếu mô tả thì dùng tên quyền
        assertEquals(List.of(new WorkspacePermission("document:upload", "document:upload"),
            new WorkspacePermission("request:create", "Tạo yêu cầu")), members.myPermissions("lab-a"));
    }

    @Test void permissionErrorsAreNotHidden() {
        when(directory.members("lab-b")).thenThrow(CoreException.forbidden("Workspace access denied"));
        assertThrows(CoreException.class, () -> members.displayNames("lab-b"));
        assertThrows(CoreException.class, () -> members.members("lab-b"));
    }
}
