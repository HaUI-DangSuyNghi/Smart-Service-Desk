package vn.haui.dx.core.identity.internal;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import vn.haui.dx.core.common.CoreException;
import vn.haui.dx.core.identity.api.DirectoryMember;
import vn.haui.dx.core.identity.api.DirectoryPermission;
import vn.haui.dx.core.identity.api.WorkspaceAccess;
import vn.haui.dx.core.identity.api.WorkspaceDirectory;
import vn.haui.dx.core.identity.api.WorkspaceRole;

/**
 * Danh bạ workspace đọc qua Keycloak Admin API bằng service account {@code dx-directory}
 * (chỉ có quyền xem user/group). Kết quả cache {@value #CACHE_SECONDS} giây cho mỗi workspace.
 * Cấu trúc group giống token: {@code /workspaces/{workspace}/{role}}.
 */
@Component
class KeycloakWorkspaceDirectory implements WorkspaceDirectory {
    static final int CACHE_SECONDS = 60;
    private static final Logger LOG = LoggerFactory.getLogger(KeycloakWorkspaceDirectory.class);
    // Gia hạn token sớm một chút để không gửi token vừa hết hạn
    private static final long TOKEN_SAFETY_SECONDS = 30;
    /** Client chỉ chứa danh sách quyền (xem infra/keycloak/realm-template.json). */
    static final String PERMISSION_CLIENT = "dx-permissions";

    private record Snapshot(String displayName, List<DirectoryMember> members,
                            Map<String, List<DirectoryPermission>> permissions, Instant expiresAt) {}
    private record Token(String value, Instant expiresAt) {}

    private final WorkspaceAccess access;
    private final RestClient http;
    private final String realm;
    private final String clientId;
    private final String clientSecret;
    private final Clock clock;
    private final Map<String, Snapshot> cache = new ConcurrentHashMap<>();
    private volatile Token token;

    @Autowired
    KeycloakWorkspaceDirectory(WorkspaceAccess access, RestClient.Builder builder,
                               @Value("${dx.directory.url:}") String url,
                               @Value("${dx.directory.realm:dx-lab}") String realm,
                               @Value("${dx.directory.client-id:dx-directory}") String clientId,
                               @Value("${dx.directory.client-secret:}") String clientSecret) {
        this(access, url.isBlank() ? null : builder.baseUrl(url).build(), realm, clientId, clientSecret, Clock.systemUTC());
    }

    /** Cho test: truyền RestClient (có thể gắn MockRestServiceServer) và đồng hồ giả. */
    KeycloakWorkspaceDirectory(WorkspaceAccess access, RestClient http, String realm, String clientId,
                               String clientSecret, Clock clock) {
        this.access = access;
        this.http = http;
        this.realm = realm;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.clock = clock;
    }

    @Override
    public List<DirectoryMember> members(String workspace) {
        access.requireMember(workspace);
        return snapshot(workspace).members();
    }

    @Override
    public String displayName(String workspace) {
        access.requireMember(workspace);
        return snapshot(workspace).displayName();
    }

    @Override
    public Map<String, List<DirectoryPermission>> rolePermissions(String workspace) {
        access.requireMember(workspace);
        return snapshot(workspace).permissions();
    }

    private Snapshot snapshot(String workspace) {
        Snapshot cached = cache.get(workspace);
        if (cached != null && cached.expiresAt().isAfter(clock.instant())) return cached;
        if (http == null || clientSecret == null || clientSecret.isBlank()) {
            throw CoreException.unavailable("Workspace directory is not configured");
        }
        try {
            Snapshot loaded = load(workspace);
            cache.put(workspace, loaded);
            return loaded;
        } catch (RestClientException error) {
            // Không đưa chi tiết lỗi Keycloak ra client, chỉ ghi log
            LOG.warn("Keycloak directory lookup failed for workspace {}: {}", workspace, error.getMessage());
            throw CoreException.unavailable("Workspace directory unavailable");
        }
    }

    private Snapshot load(String workspace) {
        Instant expiresAt = clock.instant().plus(Duration.ofSeconds(CACHE_SECONDS));
        JsonNode group = get("/admin/realms/{realm}/group-by-path/workspaces/{workspace}", realm, workspace);
        if (group == null) return new Snapshot(workspace, List.of(), Map.of(), expiresAt);

        String displayName = group.path("attributes").path("displayName").path(0).asText("");
        Map<String, JsonNode> users = new LinkedHashMap<>();
        Map<String, Set<WorkspaceRole>> roles = new LinkedHashMap<>();
        Map<String, List<DirectoryPermission>> permissions = new LinkedHashMap<>();

        JsonNode roleGroups = get("/admin/realms/{realm}/groups/{id}/children?briefRepresentation=true&max=100",
            realm, group.path("id").asText());
        for (JsonNode roleGroup : iterable(roleGroups)) {
            String role = roleGroup.path("name").asText();
            // Bỏ group không đúng mẫu vai trò, giống cách đọc token (JwtCurrentUser)
            if (!WorkspaceRole.NAME.matcher(role).matches()) continue;
            JsonNode members = get("/admin/realms/{realm}/groups/{id}/members?briefRepresentation=true&max=1000",
                realm, roleGroup.path("id").asText());
            for (JsonNode user : iterable(members)) {
                if (!user.path("enabled").asBoolean(true)) continue;
                String sub = user.path("id").asText();
                users.putIfAbsent(sub, user);
                roles.computeIfAbsent(sub, key -> new HashSet<>()).add(new WorkspaceRole(role));
            }
            permissions.put(role, permissions(roleGroup.path("id").asText()));
        }

        List<DirectoryMember> result = new ArrayList<>();
        users.forEach((sub, user) -> result.add(new DirectoryMember(
            sub, user.path("username").asText(sub), fullName(user), roles.get(sub))));
        result.sort(Comparator.comparing(DirectoryMember::username));
        return new Snapshot(displayName.isBlank() ? workspace : displayName, List.copyOf(result),
            Map.copyOf(permissions), expiresAt);
    }

    /** Quyền (role của client dx-permissions) gán trực tiếp cho một group vai trò. */
    private List<DirectoryPermission> permissions(String groupId) {
        JsonNode mappings = get("/admin/realms/{realm}/groups/{id}/role-mappings", realm, groupId);
        if (mappings == null) return List.of();
        List<DirectoryPermission> result = new ArrayList<>();
        for (JsonNode role : iterable(mappings.path("clientMappings").path(PERMISSION_CLIENT).path("mappings"))) {
            String description = role.path("description").asText("");
            result.add(new DirectoryPermission(role.path("name").asText(), description.isBlank() ? null : description));
        }
        result.sort(Comparator.comparing(DirectoryPermission::name));
        return List.copyOf(result);
    }

    /** GET Admin API; 404 → null, lỗi khác → RestClientException. */
    private JsonNode get(String uri, Object... variables) {
        return http.get().uri(uri, variables)
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken())
            .exchange((request, response) -> {
                int status = response.getStatusCode().value();
                if (status == 404) return null;
                if (status == 401) token = null; // token bị thu hồi: lần sau xin token mới
                if (!response.getStatusCode().is2xxSuccessful()) {
                    throw new RestClientException("Keycloak admin API returned " + status);
                }
                return response.bodyTo(JsonNode.class);
            });
    }

    /** Token client_credentials của dx-directory, dùng lại đến gần lúc hết hạn. */
    private String accessToken() {
        Token current = token;
        if (current != null && current.expiresAt().isAfter(clock.instant())) return current.value();

        var form = new LinkedMultiValueMap<String, String>();
        form.add("grant_type", "client_credentials");
        form.add("client_id", clientId);
        form.add("client_secret", clientSecret);
        JsonNode body = http.post().uri("/realms/{realm}/protocol/openid-connect/token", realm)
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .body(form)
            .retrieve()
            .body(JsonNode.class);
        if (body == null || !body.hasNonNull("access_token")) {
            throw new RestClientException("Keycloak token response has no access_token");
        }
        long lifetime = Math.max(0, body.path("expires_in").asLong(60) - TOKEN_SAFETY_SECONDS);
        token = new Token(body.get("access_token").asText(), clock.instant().plusSeconds(lifetime));
        return token.value();
    }

    private static String fullName(JsonNode user) {
        String name = (user.path("firstName").asText("") + " " + user.path("lastName").asText("")).trim();
        return name.isEmpty() ? null : name;
    }

    private static Iterable<JsonNode> iterable(JsonNode array) {
        return array == null || !array.isArray() ? List.of() : array;
    }
}
