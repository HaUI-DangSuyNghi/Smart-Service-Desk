package vn.haui.dx.hpdi.intelligence.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;
import vn.haui.dx.hpdi.common.api.CapabilityException;
import vn.haui.dx.hpdi.common.api.SecretFiles;
import vn.haui.dx.hpdi.intelligence.api.IntelligenceFailure;
import java.net.URI;
import java.net.ProxySelector;
import java.net.http.*;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/** Development-only IAM bootstrap. Actual signed worker identity and live grants are still required. */
@Component
public final class AutomaticWorkspaceProvisioning {
    private final boolean enabled;
    private final URI base;
    private final String tokenFile;
    private final ObjectMapper json;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1))
            .proxy(ProxySelector.of(null)).followRedirects(HttpClient.Redirect.NEVER).build();

    public AutomaticWorkspaceProvisioning(
            @Value("${dx.background.auto-workspaces.enabled:false}") boolean enabled,
            @Value("${dx.background.auto-workspaces.base-url:}") String endpoint,
            @Value("${dx.background.auto-workspaces.token-file:}") String tokenFile,
            Environment environment, ObjectMapper json) {
        this.enabled = enabled; this.tokenFile = tokenFile;
        this.json = json.copy().enable(com.fasterxml.jackson.core.JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
                .enable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
        base = enabled ? URI.create(endpoint) : null;
        if (enabled && (!environment.acceptsProfiles(Profiles.of("local-dev"))
                || environment.acceptsProfiles(Profiles.of("prod", "production"))
                || !"http".equals(base.getScheme()) || base.getHost() == null || !Set.of("workspace-bootstrap", "127.0.0.1", "localhost").contains(base.getHost())
                || base.getPort() < 1 || base.getUserInfo() != null || base.getQuery() != null || base.getFragment() != null
                || !(base.getPath().isEmpty() || "/".equals(base.getPath())))) {
            throw new IllegalStateException("Automatic workspace provisioning requires an internal local-dev endpoint");
        }
    }

    public void prepare(String workspace) {
        if (!enabled) return;
        if (workspace == null || !workspace.matches("[a-zA-Z0-9_-]{1,100}")) throw CapabilityException.forbidden("Invalid workspace");
        var request = HttpRequest.newBuilder(base.resolve("/internal/workspaces/" + workspace))
                .timeout(Duration.ofSeconds(5)).header("Authorization", "Bearer " + SecretFiles.resolve("", tokenFile))
                .POST(HttpRequest.BodyPublishers.noBody()).build();
        var pending = http.sendAsync(request, info -> {
            boolean bounded = false;
            try {
                long size = Long.parseLong(info.headers().firstValue("Content-Length").orElse("-1"));
                bounded = size >= 0 && size <= 4096 && info.headers().firstValue("Transfer-Encoding").isEmpty()
                        && info.headers().firstValue("Content-Encoding").isEmpty();
            } catch (NumberFormatException ignored) { }
            return bounded ? HttpResponse.BodyHandlers.ofByteArray().apply(info)
                    : HttpResponse.BodyHandlers.replacing(new byte[0]).apply(info);
        });
        try {
            var response = pending.get(5, TimeUnit.SECONDS);
            if (response.statusCode() == 403 || response.statusCode() == 404) throw CapabilityException.forbidden("Workspace background permission is unavailable or revoked");
            if (response.statusCode() != 200) throw IntelligenceFailure.unavailable();
            var body = json.readTree(response.body());
            var fields = new java.util.HashSet<String>();
            if (body == null || !body.isObject()) throw IntelligenceFailure.invalid();
            body.fieldNames().forEachRemaining(fields::add);
            if (!fields.equals(Set.of("workspace", "ready")) || !workspace.equals(body.path("workspace").asText())
                    || !body.path("ready").isBoolean() || !body.path("ready").booleanValue()) throw IntelligenceFailure.invalid();
        } catch (InterruptedException failure) {
            pending.cancel(true); Thread.currentThread().interrupt(); throw IntelligenceFailure.unavailable();
        } catch (java.util.concurrent.ExecutionException | java.util.concurrent.TimeoutException failure) {
            pending.cancel(true); throw IntelligenceFailure.unavailable();
        } catch (java.io.IOException failure) { throw IntelligenceFailure.invalid(); }
    }
}
