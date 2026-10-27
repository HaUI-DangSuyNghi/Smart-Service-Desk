package vn.haui.dx.core.identity.internal;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import vn.haui.dx.core.common.CoreException;
import vn.haui.dx.core.identity.api.ServiceIdentity;
import vn.haui.dx.core.identity.api.WorkspacePermissions;

@Service
public class JwtServiceIdentity implements ServiceIdentity {
    private final JwtDecoder decoder;
    private final WorkspacePermissions permissions;
    private final String tokenUrl;
    private final String clientId;
    private final String secret;
    private final com.fasterxml.jackson.databind.ObjectMapper json;
    private final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1)).followRedirects(HttpClient.Redirect.NEVER).build();

    public JwtServiceIdentity(JwtDecoder decoder, WorkspacePermissions permissions,
            @Value("${dx.background.token-url:}") String tokenUrl,
            @Value("${dx.background.client-id:dx-assessment-worker}") String clientId,
            @Value("${dx.background.client-secret:}") String secret, com.fasterxml.jackson.databind.ObjectMapper json) {
        this.decoder=decoder; this.permissions=permissions; this.tokenUrl=tokenUrl; this.clientId=clientId;
        this.secret=secret; this.json=json;
    }

    @Override public void run(String workspace, String permission, Runnable operation) {
        var previous=SecurityContextHolder.getContext();
        try {
            if (tokenUrl.isBlank() || secret.isBlank())
                throw CoreException.forbidden("Background identity is not configured");
            var endpoint=URI.create(tokenUrl);
            if (!("https".equals(endpoint.getScheme()) || "http".equals(endpoint.getScheme()))
                    || endpoint.getHost()==null || endpoint.getUserInfo()!=null || endpoint.getFragment()!=null)
                throw CoreException.unavailable("Background identity unavailable");
            var request=HttpRequest.newBuilder(endpoint).timeout(Duration.ofSeconds(3))
                .header("Content-Type","application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString("grant_type=client_credentials&client_id="+URLEncoder.encode(clientId,StandardCharsets.UTF_8)
                    +"&client_secret="+URLEncoder.encode(secret,StandardCharsets.UTF_8))).build();
            var pending=http.sendAsync(request,info -> new TokenBody());
            HttpResponse<byte[]> response;
            try { response=pending.get(3,java.util.concurrent.TimeUnit.SECONDS); }
            catch (java.util.concurrent.TimeoutException | java.util.concurrent.ExecutionException failure) {
                pending.cancel(true);
                throw CoreException.unavailable("Background identity unavailable");
            } catch (InterruptedException failure) {
                pending.cancel(true); throw failure;
            }
            if (response.statusCode()==400 || response.statusCode()==401 || response.statusCode()==403)
                throw CoreException.forbidden("Background service credential rejected");
            if (response.statusCode()!=200) throw CoreException.unavailable("Background identity unavailable");
            var body=json.readTree(response.body());
            if (body==null || !body.path("access_token").isTextual()) throw CoreException.unavailable("Background identity unavailable");
            String encoded=body.path("access_token").asText();
            var jwt=decoder.decode(encoded);
            if (!clientId.equals(jwt.getClaimAsString("azp"))
                    || !("service-account-"+clientId).equals(jwt.getClaimAsString("preferred_username"))
                    || jwt.getExpiresAt()==null || !jwt.getExpiresAt().isAfter(Instant.now()))
                throw CoreException.forbidden("Background service identity required");
            var context=SecurityContextHolder.createEmptyContext();
            context.setAuthentication(new JwtAuthenticationToken(jwt));
            SecurityContextHolder.setContext(context);
            permissions.require(workspace,permission);
            operation.run();
        } catch (org.springframework.security.oauth2.jwt.JwtException failure) {
            throw CoreException.forbidden("Background service token rejected");
        } catch (java.io.IOException failure) {
            throw CoreException.unavailable("Background identity unavailable");
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw CoreException.unavailable("Background identity unavailable");
        } finally { SecurityContextHolder.setContext(previous); }
    }

    private static final class TokenBody implements HttpResponse.BodySubscriber<byte[]> {
        private final java.util.concurrent.CompletableFuture<byte[]> result=new java.util.concurrent.CompletableFuture<>();
        private final java.io.ByteArrayOutputStream bytes=new java.io.ByteArrayOutputStream();
        private java.util.concurrent.Flow.Subscription subscription;
        @Override public java.util.concurrent.CompletionStage<byte[]> getBody() { return result; }
        @Override public void onSubscribe(java.util.concurrent.Flow.Subscription value) { subscription=value; subscription.request(1); }
        @Override public void onNext(java.util.List<java.nio.ByteBuffer> buffers) {
            for (var buffer:buffers) {
                if (buffer.remaining()>16384-bytes.size()) {
                    subscription.cancel(); result.completeExceptionally(new java.io.IOException("Token response too large")); return;
                }
                byte[] chunk=new byte[buffer.remaining()]; buffer.get(chunk); bytes.writeBytes(chunk);
            }
            subscription.request(1);
        }
        @Override public void onError(Throwable failure) { result.completeExceptionally(failure); }
        @Override public void onComplete() { result.complete(bytes.toByteArray()); }
    }
}
