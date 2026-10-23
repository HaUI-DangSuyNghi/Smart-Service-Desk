package vn.haui.dx.core.identity.internal;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import vn.haui.dx.core.common.CoreException;
import vn.haui.dx.core.common.ErrorCode;
import vn.haui.dx.core.identity.api.WorkspaceAccess;
import vn.haui.dx.core.identity.api.WorkspaceRole;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class KeycloakWorkspaceDirectoryTest {
    private static final String KC = "http://kc";
    private final WorkspaceAccess access = mock(WorkspaceAccess.class);
    private MockRestServiceServer server;
    private KeycloakWorkspaceDirectory directory;

    @BeforeEach void setUp() {
        var builder = RestClient.builder().baseUrl(KC);
        server = MockRestServiceServer.bindTo(builder).build();
        directory = new KeycloakWorkspaceDirectory(access, builder.build(), "dx-lab", "dx-directory", "secret",
            Clock.fixed(Instant.parse("2026-10-24T00:00:00Z"), ZoneOffset.UTC));
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
            "[{\"id\":\"s2\",\"username\":\"user2\",\"firstName\":\"Demo\",\"lastName\":\"Agent\"},"
                + "{\"id\":\"s9\",\"username\":\"superadmin\"},"
                + "{\"id\":\"off\",\"username\":\"disabled\",\"enabled\":false}]");
        expectGet("/admin/realms/dx-lab/groups/r2/members?briefRepresentation=true&max=1000",
            "[{\"id\":\"s9\",\"username\":\"superadmin\"}]");

        var members = directory.members("lab-a");
        assertEquals("Phòng máy A", directory.displayName("lab-a")); // lấy từ cache, không gọi lại Keycloak
        server.verify();

        verify(access, times(2)).requireMember("lab-a");
        assertEquals(2, members.size());
        assertEquals("superadmin", members.get(0).username());
        assertEquals(Set.of(WorkspaceRole.AGENT, WorkspaceRole.COORDINATOR), members.get(0).roles());
        assertNull(members.get(0).fullName());
        assertEquals("Demo Agent", members.get(1).fullName());
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

    @Test void membershipIsCheckedBeforeAnyLookup() {
        doThrow(CoreException.forbidden("Workspace access denied")).when(access).requireMember("lab-b");
        var error = assertThrows(CoreException.class, () -> directory.members("lab-b"));
        assertEquals(ErrorCode.FORBIDDEN, error.code());
        server.verify(); // không có request nào tới Keycloak
    }
}
