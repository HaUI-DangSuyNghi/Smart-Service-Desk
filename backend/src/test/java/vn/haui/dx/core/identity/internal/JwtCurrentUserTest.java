package vn.haui.dx.core.identity.internal;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import vn.haui.dx.core.common.CoreException;
import vn.haui.dx.core.common.ErrorCode;
import vn.haui.dx.core.identity.api.Membership;
import vn.haui.dx.core.identity.api.WorkspaceRole;
import static org.junit.jupiter.api.Assertions.*;
import static vn.haui.dx.core.identity.api.WorkspaceRole.*;

class JwtCurrentUserTest {
    private static Jwt token(Map<String, Object> claims) {
        return new Jwt("test", Instant.now(), Instant.now().plusSeconds(60), Map.of("alg", "RS256"), claims);
    }

    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    @Test void readsOnlyWellFormedWorkspaceGroups() {
        var user = JwtCurrentUser.from(token(Map.of("sub", "u2", "preferred_username", "user2", "groups", List.of(
            "/workspaces/lab-a/agent", "/workspaces/lab-b/technician",
            "/lab-a", "/workspaces/lab-a", "/workspaces/lab-a/../lab-c/agent", "/workspaces/Lab-A/agent", "/other/lab-a/agent"))));
        assertEquals("user2", user.username());
        assertEquals(java.util.Set.of(new Membership("lab-a", AGENT), new Membership("lab-b", new WorkspaceRole("technician"))),
            user.memberships());
        assertTrue(user.hasRole("lab-a", REQUESTER, AGENT));
        assertFalse(user.hasRole("lab-a", COORDINATOR));
        assertFalse(user.isMember("lab-c"));
        assertFalse(user.platformAdmin());
    }

    @Test void platformAdminIsNotWorkspaceMember() {
        var admin = JwtCurrentUser.from(token(Map.of("sub", "u7", "realm_access", Map.of("roles", List.of("platform-admin")))));
        assertTrue(admin.platformAdmin());
        assertFalse(admin.isMember("lab-a"));
        assertEquals("u7", admin.username());
    }

    @Test void missingOrMalformedClaimsGrantNothing() {
        var user = JwtCurrentUser.from(token(Map.of("sub", "u0", "groups", "not-a-list", "realm_access", "x")));
        assertTrue(user.memberships().isEmpty());
        assertFalse(user.platformAdmin());
    }

    @Test void workspaceAccessDeniesWithForbidden() {
        SecurityContextHolder.getContext().setAuthentication(
            new JwtAuthenticationToken(token(Map.of("sub", "u1", "groups", List.of("/workspaces/lab-a/requester")))));
        var access = new WorkspaceAccessImpl();
        assertDoesNotThrow(() -> access.requireMember("lab-a"));
        assertEquals(ErrorCode.FORBIDDEN, assertThrows(CoreException.class, () -> access.requireMember("lab-b")).code());
        assertEquals(ErrorCode.FORBIDDEN, assertThrows(CoreException.class, () -> access.requireRole("lab-a", AGENT, COORDINATOR)).code());
    }

    @Test void workspaceAccessWithoutTokenIsUnauthenticated() {
        assertEquals(ErrorCode.UNAUTHENTICATED, assertThrows(CoreException.class, () -> new WorkspaceAccessImpl().current()).code());
    }

    @Test void roleNamesAreValidated() {
        assertThrows(IllegalArgumentException.class, () -> new WorkspaceRole("Agent"));
        assertThrows(IllegalArgumentException.class, () -> new WorkspaceRole("../x"));
        assertEquals("lab-a:coordinator", new Membership("lab-a", COORDINATOR).candidateGroup());
    }
}
