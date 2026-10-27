package vn.haui.dx.core.identity.internal;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import vn.haui.dx.core.common.CoreException;
import vn.haui.dx.core.common.ErrorCode;
import vn.haui.dx.core.identity.api.DirectoryPermission;
import vn.haui.dx.core.identity.api.WorkspaceAccess;
import vn.haui.dx.core.identity.api.WorkspaceRole;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class KeycloakWorkspaceDirectoryTest {
    @Test void supportGroupsUseSeparateNamespaceAndIgnoreDisabledMembers() {
        expectToken();
        expectGet("/admin/realms/dx-lab/group-by-path/support-groups/lab-a/hardware", "{\"id\":\"support\"}");
        expectGet("/admin/realms/dx-lab/groups/support/members?briefRepresentation=true&first=0&max=1000",
            "[{\"id\":\"agent\"},{\"id\":\"disabled\",\"enabled\":false}]");
        assertEquals(java.util.Set.of("agent"), directory.supportGroupMembers("lab-a", "hardware"));
        verify(access).requireMember("lab-a");
        server.verify();
    }

    @Test void supportGroupLookupFailsClosedAndRequiresWorkspaceMembership() {
        doThrow(CoreException.forbidden("Workspace required")).when(access).requireMember("lab-b");
        assertThrows(CoreException.class, () -> directory.supportGroupMembers("lab-b", "hardware"));
        assertThrows(CoreException.class, () -> directory.supportGroupMembers("lab-a", "../agent"));
        server.verify();
    }
    private static final String KC = "http://kc";
    private final WorkspaceAccess access = mock(WorkspaceAccess.class);
    private MockRestServiceServer server;
    private KeycloakWorkspaceDirectory directory;
    private final Clock clock = mock(Clock.class);

    @BeforeEach void setUp() {
        var builder = RestClient.builder().baseUrl(KC);
        server = MockRestServiceServer.bindTo(builder).build();
        when(clock.instant()).thenReturn(Instant.parse("2026-10-24T00:00:00Z"));
        directory = new KeycloakWorkspaceDirectory(access, builder.build(), "dx-lab", "dx-directory", "secret", clock);
    }

    private void expectToken() {
        server.expect(requestTo(KC + "/realms/dx-lab/protocol/openid-connect/token"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("grant_type=client_credentials")))
            .andRespond(withSuccess("{\"access_token\":\"t\",\"expires_in\":300}", MediaType.APPLICATION_JSON));
    }

    private void expectGet(String path, String json) {
        server.expect(requestTo(KC + path)).andExpect(method(HttpMethod.GET))
            .andExpect(header("Authorization", "Bearer t"))
            .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));
    }

    @Test void mergesRolesPerUserAndReadsDisplayNameOnceThenCaches() {
        expectToken();
        expectGet("/admin/realms/dx-lab/group-by-path/workspaces/lab-a",
            "{\"id\":\"g\",\"attributes\":{\"displayName\":[\"Phòng máy A\"]}}");
        expectGet("/admin/realms/dx-lab/groups/g/children?briefRepresentation=true&max=100",
            "[{\"id\":\"r1\",\"name\":\"agent\"},{\"id\":\"r2\",\"name\":\"coordinator\"},{\"id\":\"bad\",\"name\":\"Bad Role\"}]");
        expectGet("/admin/realms/dx-lab/groups/r1/members?briefRepresentation=true&max=1000",
            "[{\"id\":\"s2\",\"username\":\"user2\",\"firstName\":\"Thị Bình\",\"lastName\":\"Trần\"},"
                + "{\"id\":\"s9\",\"username\":\"superadmin\"},"
                + "{\"id\":\"off\",\"username\":\"disabled\",\"enabled\":false}]");
        expectGet("/admin/realms/dx-lab/groups/r1/role-mappings",
            "{\"clientMappings\":{\"dx-permissions\":{\"mappings\":["
                + "{\"name\":\"request:create\",\"description\":\"Tạo yêu cầu\"},"
                + "{\"name\":\"document:upload\",\"description\":\"Tải tài liệu lên\"}]},"
                + "\"other-client\":{\"mappings\":[{\"name\":\"ignored\"}]}}}");
        expectGet("/admin/realms/dx-lab/groups/r2/members?briefRepresentation=true&max=1000",
            "[{\"id\":\"s9\",\"username\":\"superadmin\"}]");
        expectGet("/admin/realms/dx-lab/groups/r2/role-mappings", "{\"realmMappings\":[]}");

        var members = directory.members("lab-a");
        assertEquals("Phòng máy A", directory.displayName("lab-a")); // lấy từ cache, không gọi lại Keycloak
        var permissions = directory.rolePermissions("lab-a");
        server.verify();

        verify(access, times(3)).requireMember("lab-a");
        // Chỉ lấy role của client dx-permissions, sắp theo tên; group không gán quyền → danh sách rỗng
        assertEquals(List.of(new DirectoryPermission("document:upload", "Tải tài liệu lên"),
            new DirectoryPermission("request:create", "Tạo yêu cầu")), permissions.get("agent"));
        assertEquals(List.of(), permissions.get("coordinator"));
        assertEquals(2, members.size());
        assertEquals("superadmin", members.get(0).username());
        assertEquals(Set.of(WorkspaceRole.AGENT, WorkspaceRole.COORDINATOR), members.get(0).roles());
        assertNull(members.get(0).fullName());
        // Họ (lastName) đứng trước tên (firstName)
        assertEquals("Trần Thị Bình", members.get(1).fullName());
    }

    @Test void unknownWorkspaceGroupMeansNoMembersAndCodeAsName() {
        expectToken();
        server.expect(requestTo(KC + "/admin/realms/dx-lab/group-by-path/workspaces/lab-z"))
            .andRespond(withResourceNotFound());

        assertTrue(directory.members("lab-z").isEmpty());
        assertEquals("lab-z", directory.displayName("lab-z"));
    }

    @Test void keycloakFailureIsServiceUnavailable() {
        expectToken();
        server.expect(requestTo(KC + "/admin/realms/dx-lab/group-by-path/workspaces/lab-a"))
            .andRespond(withServerError());

        var error = assertThrows(CoreException.class, () -> directory.members("lab-a"));
        assertEquals(ErrorCode.SERVICE_UNAVAILABLE, error.code());
    }

    @Test void notConfiguredIsServiceUnavailableWithoutCallingKeycloak() {
        var unconfigured = new KeycloakWorkspaceDirectory(access, null, "dx-lab", "dx-directory", "",
            Clock.systemUTC());
        var error = assertThrows(CoreException.class, () -> unconfigured.members("lab-a"));
        assertEquals(ErrorCode.SERVICE_UNAVAILABLE, error.code());
    }

    @Test void permissionRevocationIsEffectiveAtCacheExpiryAndOutageNeverRestoresStaleGrant() {
        expectToken();
        expectPermissionSnapshot("{\"name\":\"task:handle\"}");
        expectPermissionSnapshot("");
        server.expect(requestTo(KC + "/admin/realms/dx-lab/group-by-path/workspaces/lab-a"))
            .andRespond(withServerError());
        var user = mock(vn.haui.dx.core.identity.api.CurrentUser.class);
        when(access.current()).thenReturn(user);
        when(user.isMember("lab-a")).thenReturn(true);
        when(user.memberships()).thenReturn(Set.of(new vn.haui.dx.core.identity.api.Membership("lab-a", WorkspaceRole.AGENT)));
        var policy = new vn.haui.dx.core.identity.api.WorkspacePermissions(access, directory);
        assertDoesNotThrow(() -> policy.require("lab-a", "task:handle"));
        when(clock.instant()).thenReturn(Instant.parse("2026-10-24T00:00:59Z"));
        assertDoesNotThrow(() -> policy.require("lab-a", "task:handle"));
        when(clock.instant()).thenReturn(Instant.parse("2026-10-24T00:01:00Z"));
        assertEquals(ErrorCode.FORBIDDEN, assertThrows(CoreException.class, () -> policy.require("lab-a", "task:handle")).code());
        when(clock.instant()).thenReturn(Instant.parse("2026-10-24T00:02:00Z"));
        assertEquals(ErrorCode.SERVICE_UNAVAILABLE, assertThrows(CoreException.class, () -> policy.require("lab-a", "task:handle")).code());
        server.verify();
    }

    private void expectPermissionSnapshot(String permissions) {
        expectGet("/admin/realms/dx-lab/group-by-path/workspaces/lab-a", "{\"id\":\"g\"}");
        expectGet("/admin/realms/dx-lab/groups/g/children?briefRepresentation=true&max=100", "[{\"id\":\"r\",\"name\":\"agent\"}]");
        expectGet("/admin/realms/dx-lab/groups/r/members?briefRepresentation=true&max=1000", "[]");
        expectGet("/admin/realms/dx-lab/groups/r/role-mappings", "{\"clientMappings\":{\"dx-permissions\":{\"mappings\":[" + permissions + "]}}}");
    }

    @Test void explicitRefreshSeesRevocationBeforeCacheExpiryAndFailsClosedOnOutage() {
        expectToken();
        expectPermissionSnapshot("{\"name\":\"task:handle\"}");
        expectPermissionSnapshot("");
        server.expect(requestTo(KC + "/admin/realms/dx-lab/group-by-path/workspaces/lab-a"))
            .andRespond(withServerError());
        assertFalse(directory.rolePermissions("lab-a").get("agent").isEmpty());
        directory.refresh("lab-a");
        assertTrue(directory.rolePermissions("lab-a").get("agent").isEmpty());
        assertEquals(ErrorCode.SERVICE_UNAVAILABLE,assertThrows(CoreException.class,() -> directory.refresh("lab-a")).code());
        server.verify();
    }

    @Test void membershipIsCheckedBeforeAnyLookup() {
        doThrow(CoreException.forbidden("Workspace access denied")).when(access).requireMember("lab-b");
        var error = assertThrows(CoreException.class, () -> directory.members("lab-b"));
        assertEquals(ErrorCode.FORBIDDEN, error.code());
        server.verify(); // không có request nào tới Keycloak
    }
}
