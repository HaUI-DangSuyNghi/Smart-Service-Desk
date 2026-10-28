package vn.haui.dx.core.identity.internal;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;
import vn.haui.dx.core.common.CoreException;
import vn.haui.dx.core.common.ErrorCode;
import vn.haui.dx.core.identity.api.FreshAdminAuthorization;
import vn.haui.dx.core.identity.api.WorkspaceAccess;

@Component
final class KeycloakFreshAdminAuthorization implements FreshAdminAuthorization {
    private final WorkspaceAccess access;
    private final ObjectMapper json;
    private final HttpClient http;
    private final String url;
    private final String realm;
    private final String clientId;
    private final String secret;
    private final Duration budget;

    @Autowired
    KeycloakFreshAdminAuthorization(WorkspaceAccess access, ObjectMapper json,
            @Value("${dx.directory.url:}") String url,
            @Value("${dx.directory.realm:dx-lab}") String realm,
            @Value("${dx.directory.client-id:dx-directory}") String clientId,
            @Value("${dx.directory.client-secret:}") String secret) {
        this(access, json, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2))
            .followRedirects(HttpClient.Redirect.NEVER).build(), url, realm, clientId, secret, Duration.ofSeconds(5));
    }

    KeycloakFreshAdminAuthorization(WorkspaceAccess access, ObjectMapper json, HttpClient http,
            String url, String realm, String clientId, String secret, Duration budget) {
        this.access = access; this.json = json; this.http = http; this.url = url;
        this.realm = realm; this.clientId = clientId; this.secret = secret; this.budget = budget;
    }

    @Override
    public String requirePlatformAdmin() {
        String subject = access.current().sub();
        if (subject == null || subject.isBlank() || subject.equals(".") || subject.equals(".."))
            throw new CoreException(ErrorCode.UNAUTHENTICATED, "Authenticated subject required");
        long deadline = System.nanoTime() + budget.toNanos();
        try {
            if (url == null || url.isBlank() || realm == null || realm.isBlank()
                    || clientId == null || clientId.isBlank() || secret == null || secret.isBlank())
                throw unavailable();
            var tokenRequest = HttpRequest.newBuilder(uri("realms", realm, "protocol", "openid-connect", "token"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString("grant_type=client_credentials&client_id="
                    + form(clientId) + "&client_secret=" + form(secret)));
            var tokenResponse = send(tokenRequest, deadline);
            if (tokenResponse.statusCode() != 200) throw unavailable();
            JsonNode token = read(tokenResponse.body());
            if (token == null || !token.path("access_token").isTextual()
                    || token.path("access_token").asText().isBlank()) throw unavailable();
            String bearer = "Bearer " + token.path("access_token").asText();
            var accountResponse = send(HttpRequest.newBuilder(uri("admin", "realms", realm, "users", subject))
                .header("Authorization", bearer).GET(), deadline);
            if (accountResponse.statusCode() == 404) throw denied();
            if (accountResponse.statusCode() != 200) throw unavailable();
            JsonNode account = read(accountResponse.body());
            if (account == null || !account.isObject() || !account.path("id").isTextual()
                    || !subject.equals(account.path("id").asText()) || !account.path("enabled").isBoolean())
                throw unavailable();
            if (!account.path("enabled").booleanValue() || account.hasNonNull("serviceAccountClientId")) throw denied();
            var rolesResponse = send(HttpRequest.newBuilder(URI.create(uri("admin", "realms", realm, "users", subject,
                    "role-mappings", "realm", "composite") + "?briefRepresentation=true"))
                .header("Authorization", bearer).GET(), deadline);
            if (rolesResponse.statusCode() != 200) throw unavailable();
            JsonNode roles = read(rolesResponse.body());
            if (roles == null || !roles.isArray()) throw unavailable();
            boolean admin = false;
            for (JsonNode role : roles) {
                if (!role.isObject() || !role.path("name").isTextual() || role.path("name").asText().isBlank()
                        || role.path("clientRole").asBoolean(false)) throw unavailable();
                if ("platform-admin".equals(role.path("name").asText())) admin = true;
            }
            if (System.nanoTime() >= deadline) throw unavailable();
            if (!admin) throw denied();
            return subject;
        } catch (IOException | IllegalArgumentException failure) {
            throw unavailable();
        }
    }

    private HttpResponse<String> send(HttpRequest.Builder request, long deadline) {
        long remaining = deadline - System.nanoTime();
        if (remaining <= 0) throw unavailable();
        var pending = http.sendAsync(request.timeout(Duration.ofNanos(remaining)).build(),
            HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        try {
            return pending.get(Math.max(1, deadline - System.nanoTime()), TimeUnit.NANOSECONDS);
        } catch (InterruptedException failure) {
            pending.cancel(true);
            Thread.currentThread().interrupt();
            throw unavailable();
        } catch (ExecutionException | TimeoutException failure) {
            pending.cancel(true);
            throw unavailable();
        }
    }

    private URI uri(String... segments) {
        return UriComponentsBuilder.fromUriString(url).pathSegment(segments).build().encode().toUri();
    }

    private JsonNode read(String body) throws IOException {
        return json.reader().with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .with(DeserializationFeature.FAIL_ON_READING_DUP_TREE_KEY).readTree(body);
    }

    private static String form(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }
    private static CoreException unavailable() { return CoreException.unavailable("Fresh administrator verification unavailable"); }
    private static CoreException denied() { return CoreException.forbidden("Current enabled human platform-admin required"); }
}
