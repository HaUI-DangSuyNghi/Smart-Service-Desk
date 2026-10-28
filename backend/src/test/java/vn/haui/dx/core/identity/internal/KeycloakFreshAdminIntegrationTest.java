package vn.haui.dx.core.identity.internal;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import vn.haui.dx.core.common.CoreException;
import vn.haui.dx.core.common.ErrorCode;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="FRESH_ADMIN_TEST_URL", matches="http://(127\\.0\\.0\\.1|keycloak):[0-9]+")
class KeycloakFreshAdminIntegrationTest {
    private final ObjectMapper json = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
    private final String base = System.getenv("FRESH_ADMIN_TEST_URL");
    private final String realm = "fresh-admin-proof";
    private final String password = System.getenv("FRESH_ADMIN_TEST_PASSWORD");
    private final String secret = System.getenv("FRESH_ADMIN_TEST_DIRECTORY_SECRET");
    private String admin;
    private String subject;
    private String username;
    private JsonNode platformRole;
    private KeycloakFreshAdminAuthorization guard;

    @BeforeEach void setup() throws Exception {
        admin = token("master", "admin-cli", "username=admin&password=" + encode(password) + "&grant_type=password");
        platformRole = api("GET", "/roles/platform-admin", null, 200);
        username = "fresh-" + UUID.randomUUID();
        var response = send("POST", "/admin/realms/" + realm + "/users", admin,
            json.writeValueAsString(Map.of("username", username, "enabled", true, "emailVerified", true,
                "firstName", "Fresh", "lastName", "Admin", "email", username + "@example.com",
                "credentials", new Object[]{Map.of("type", "password", "value", password, "temporary", false)})), "application/json");
        assertEquals(201, response.statusCode());
        subject = response.headers().firstValue("Location").orElseThrow().replaceAll(".*/", "");
        guard = new KeycloakFreshAdminAuthorization(new WorkspaceAccessImpl(), json, base, realm, "dx-directory", secret);
        authenticate();
    }

    private void authenticate() throws Exception {
        String jwt = token(realm, "core-probe", "username=" + username + "&password=" + encode(password) + "&grant_type=password");
        useJwt(jwt);
        assertEquals(subject, new WorkspaceAccessImpl().current().sub());
    }

    private void useJwt(String jwt) {
        var decoder = NimbusJwtDecoder.withJwkSetUri(base + "/realms/" + realm + "/protocol/openid-connect/certs").build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(base + "/realms/" + realm));
        var decoded = decoder.decode(jwt);
        assertTrue(decoded.getAudience().contains("dx-core"));
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(decoded));
    }

    @AfterEach void cleanup() throws Exception {
        SecurityContextHolder.clearContext();
        if (subject != null) api("DELETE", "/users/" + subject, null, 204);
    }

    @ParameterizedTest @ValueSource(strings={"direct", "group", "composite", "nested"})
    void liveEffectiveGrantsAndRevocationsWithSameSignedJwt(String mode) throws Exception {
        assertDenied();
        String revokePath;
        String revokeBody;
        if (mode.equals("group")) {
            String groupName = "fresh-group-" + UUID.randomUUID();
            api("POST", "/groups", json.writeValueAsString(Map.of("name", groupName)), 201);
            JsonNode groups = api("GET", "/groups?search=" + groupName, null, 200);
            String groupId = groups.get(0).path("id").asText();
            api("POST", "/groups/" + groupId + "/role-mappings/realm", array(platformRole), 204);
            api("PUT", "/users/" + subject + "/groups/" + groupId, null, 204);
            revokePath = "/users/" + subject + "/groups/" + groupId;
            revokeBody = null;
        } else if (mode.equals("direct")) {
            api("POST", "/users/" + subject + "/role-mappings/realm", array(platformRole), 204);
            revokePath = "/users/" + subject + "/role-mappings/realm";
            revokeBody = array(platformRole);
        } else {
            JsonNode parent = role("fresh-parent-" + UUID.randomUUID());
            JsonNode child = mode.equals("nested") ? role("fresh-child-" + UUID.randomUUID()) : platformRole;
            api("POST", "/roles/" + parent.path("name").asText() + "/composites", array(child), 204);
            if (mode.equals("nested")) api("POST", "/roles/" + child.path("name").asText() + "/composites", array(platformRole), 204);
            api("POST", "/users/" + subject + "/role-mappings/realm", array(parent), 204);
            revokePath = "/roles/" + parent.path("name").asText() + "/composites";
            revokeBody = array(child);
        }
        assertFalse(new WorkspaceAccessImpl().current().platformAdmin());
        assertEquals(subject, guard.requirePlatformAdmin());
        authenticate();
        assertTrue(new WorkspaceAccessImpl().current().platformAdmin());
        var original = SecurityContextHolder.getContext().getAuthentication();
        assertEquals(subject, guard.requirePlatformAdmin());
        api("DELETE", revokePath, revokeBody, 204);
        assertSame(original, SecurityContextHolder.getContext().getAuthentication());
        assertDenied();
    }

    @Test void disabledAndDeletedAccountsDenyOldSignedJwt() throws Exception {
        api("POST", "/users/" + subject + "/role-mappings/realm", array(platformRole), 204);
        assertEquals(subject, guard.requirePlatformAdmin());
        api("PUT", "/users/" + subject, "{\"enabled\":false}", 204);
        assertDenied();
        api("PUT", "/users/" + subject, "{\"enabled\":true}", 204);
        assertEquals(subject, guard.requirePlatformAdmin());
        api("DELETE", "/users/" + subject, null, 204);
        subject = null;
        assertDenied();
    }

    @Test void actualDirectoryPermissionsAreReadOnlyAndRevocationFailsClosed() throws Exception {
        api("POST", "/users/" + subject + "/role-mappings/realm", array(platformRole), 204);
        assertEquals(subject, guard.requirePlatformAdmin());
        String serviceToken = token(realm, "dx-directory", "grant_type=client_credentials&client_secret=" + encode(secret));
        assertEquals(200, send("GET", "/admin/realms/" + realm + "/users/" + subject, serviceToken, null, null).statusCode());
        assertEquals(403, send("PUT", "/admin/realms/" + realm + "/users/" + subject, serviceToken,
            "{\"enabled\":false}", "application/json").statusCode());
        assertEquals(403, send("POST", "/admin/realms/" + realm + "/users/" + subject + "/role-mappings/realm",
            serviceToken, array(platformRole), "application/json").statusCode());
        String managementId = api("GET", "/clients?clientId=realm-management", null, 200).get(0).path("id").asText();
        String directoryId = api("GET", "/clients?clientId=dx-directory", null, 200).get(0).path("id").asText();
        String serviceId = api("GET", "/clients/" + directoryId + "/service-account-user", null, 200).path("id").asText();
        JsonNode viewRole = api("GET", "/clients/" + managementId + "/roles/view-users", null, 200);
        String mapping = "/users/" + serviceId + "/role-mappings/clients/" + managementId;
        api("DELETE", mapping, array(viewRole), 204);
        try {
            assertEquals(ErrorCode.SERVICE_UNAVAILABLE, assertThrows(CoreException.class, guard::requirePlatformAdmin).code());
        } finally { api("POST", mapping, array(viewRole), 204); }
        assertEquals(subject, guard.requirePlatformAdmin());
    }

    private void assertDenied() {
        assertEquals(ErrorCode.FORBIDDEN, assertThrows(CoreException.class, guard::requirePlatformAdmin).code());
    }

    @Test void realRequesterAgentCoordinatorAndServiceAccountCannotImpersonateAdmin() throws Exception {
        for (String username : new String[]{"user1", "user2", "user3"}) {
            useJwt(token(realm, "core-probe", "username=" + username + "&password=" + encode(password) + "&grant_type=password"));
            assertDenied();
        }
        String client = "fresh-service-" + UUID.randomUUID();
        String clientSecret = UUID.randomUUID().toString();
        api("POST", "/clients", json.writeValueAsString(Map.of("clientId", client,
            "publicClient", false, "secret", clientSecret, "serviceAccountsEnabled", true,
            "standardFlowEnabled", false, "protocol", "openid-connect", "protocolMappers", new Object[]{Map.of(
                "name", "core-audience", "protocol", "openid-connect", "protocolMapper", "oidc-audience-mapper",
                "config", Map.of("included.custom.audience", "dx-core", "access.token.claim", "true"))})), 201);
        useJwt(token(realm, client, "grant_type=client_credentials&client_secret=" + encode(clientSecret)));
        assertDenied();
    }
    private JsonNode role(String name) throws Exception {
        api("POST", "/roles", json.writeValueAsString(Map.of("name", name)), 201);
        return api("GET", "/roles/" + name, null, 200);
    }
    private String array(JsonNode role) throws Exception { return json.writeValueAsString(new JsonNode[]{role}); }
    private JsonNode api(String method, String path, String body, int expected) throws Exception {
        var response = send(method, "/admin/realms/" + realm + path, admin, body, "application/json");
        assertEquals(expected, response.statusCode(), method + " " + path);
        return response.body().isBlank() ? json.nullNode() : json.readTree(response.body());
    }
    private String token(String realmName, String client, String form) throws Exception {
        var response = send("POST", "/realms/" + realmName + "/protocol/openid-connect/token", null,
            "client_id=" + client + "&" + form, "application/x-www-form-urlencoded");
        assertEquals(200, response.statusCode(), "Token request failed: "
            + (response.statusCode() == 200 ? "" : json.readTree(response.body()).path("error_description").asText()));
        return json.readTree(response.body()).path("access_token").asText();
    }
    private HttpResponse<String> send(String method, String path, String bearer, String body, String type) throws Exception {
        var request = HttpRequest.newBuilder(URI.create(base + path)).timeout(Duration.ofSeconds(10));
        if (bearer != null) request.header("Authorization", "Bearer " + bearer);
        if (type != null) request.header("Content-Type", type);
        return http.send(request.method(method, body == null ? HttpRequest.BodyPublishers.noBody()
            : HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }
    private static String encode(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }
}
