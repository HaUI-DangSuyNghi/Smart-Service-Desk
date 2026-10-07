package vn.haui.dx.core.identity;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;

class SecurityTest {
    private Jwt token() {
        return new Jwt("test", Instant.now(), Instant.now().plusSeconds(60), Map.of("alg", "RS256"),
            Map.of("sub", "alice", "groups", List.of("/lab-a"), "realm_access", Map.of("roles", List.of("core-reader"))));
    }
    @Test void allowsOnlySignedWorkspaceMembership() {
        assertDoesNotThrow(() -> Security.workspace(token(), "lab-a"));
        assertEquals(403, assertThrows(ResponseStatusException.class, () -> Security.workspace(token(), "lab-b")).getStatusCode().value());
        assertThrows(ResponseStatusException.class, () -> Security.workspace(token(), "lab-a/../lab-b"));
    }
    @Test void deniesMissingWriteRole() {
        assertDoesNotThrow(() -> Security.role(token(), "core-reader"));
        assertThrows(ResponseStatusException.class, () -> Security.role(token(), "core-writer"));
    }
    @Test void deniesAbsentClaims() {
        Jwt empty = new Jwt("test", Instant.now(), Instant.now().plusSeconds(60), Map.of("alg", "RS256"), Map.of("sub", "bob"));
        assertThrows(ResponseStatusException.class, () -> Security.role(empty, "core-reader"));
        assertThrows(ResponseStatusException.class, () -> Security.workspace(empty, "lab-a"));
    }
}
