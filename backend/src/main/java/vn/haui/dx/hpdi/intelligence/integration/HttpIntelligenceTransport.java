package vn.haui.dx.hpdi.intelligence.integration;

import vn.haui.dx.hpdi.intelligence.api.IntelligenceFailure;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

@Component
public class HttpIntelligenceTransport implements IntelligenceTransport {
    static final int MAX_REQUEST = 32 * 1024;
    static final int MAX_RESPONSE = 256 * 1024;
    private final TriageConfiguration.Settings settings;
    private final ObjectMapper json;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1))
        .followRedirects(HttpClient.Redirect.NEVER).build();

    public HttpIntelligenceTransport(TriageConfiguration.Settings settings, ObjectMapper json) {
        this.settings = settings;
        this.json = json.copy().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    }

    private URI endpoint() {
        if (settings.baseUrl() == null || settings.baseUrl().isBlank()) throw IntelligenceFailure.unavailable();
        try {
            var base = URI.create(settings.baseUrl());
            if (!("http".equals(base.getScheme()) || "https".equals(base.getScheme())) || base.getHost() == null
                    || base.getPort() == 0 || base.getPort() > 65535
                    || base.getUserInfo() != null || base.getQuery() != null || base.getFragment() != null
                    || !(base.getPath().isEmpty() || "/".equals(base.getPath()))
                    || settings.token() == null || !settings.token().matches("[A-Za-z0-9._~+/-]{32,}=*"))
                throw IntelligenceFailure.configuration();
            return base.resolve("/internal/intelligence/v1/triage");
        } catch (IllegalArgumentException exception) { throw IntelligenceFailure.configuration(); }
    }

    @Override
    public JsonNode triage(TriageRequest.Envelope input) {
        var endpoint = endpoint();
        byte[] body;
        try { body = json.writeValueAsBytes(input); }
        catch (IOException exception) { throw IntelligenceFailure.configuration(); }
        if (body.length > MAX_REQUEST) throw IntelligenceFailure.configuration();
        var request = HttpRequest.newBuilder(endpoint).timeout(Duration.ofSeconds(4))
            .header("Authorization", "Bearer " + settings.token()).header("Content-Type", "application/json")
            .header("Accept", "application/json").POST(HttpRequest.BodyPublishers.ofByteArray(body)).build();
        var pending = http.sendAsync(request, info -> new LimitedBody());
        try {
            var response = pending.get(4, TimeUnit.SECONDS);
            int status = response.statusCode();
            if (status == 401 || status == 403) throw IntelligenceFailure.configuration();
            if (status == 429 || status >= 500 && status <= 599) throw IntelligenceFailure.unavailable();
            if (status != 200) throw IntelligenceFailure.invalid();
            String mediaType = response.headers().firstValue("Content-Type").orElse("").split(";", 2)[0].trim();
            if (!"application/json".equalsIgnoreCase(mediaType)) throw IntelligenceFailure.invalid();
            try { return json.readTree(response.body()); }
            catch (IOException exception) { throw IntelligenceFailure.invalid(); }
        } catch (TimeoutException exception) {
            pending.cancel(true);
            throw IntelligenceFailure.timeout();
        } catch (InterruptedException exception) {
            pending.cancel(true);
            Thread.currentThread().interrupt();
            throw IntelligenceFailure.unavailable();
        } catch (ExecutionException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof HttpTimeoutException) throw IntelligenceFailure.timeout();
            if (cause instanceof IntelligenceFailure failure) throw failure;
            throw IntelligenceFailure.unavailable();
        }
    }

    private static final class LimitedBody implements HttpResponse.BodySubscriber<byte[]> {
        private final CompletableFuture<byte[]> result = new CompletableFuture<>();
        private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        private Flow.Subscription subscription;

        @Override public CompletionStage<byte[]> getBody() { return result; }
        @Override public void onSubscribe(Flow.Subscription value) { subscription = value; value.request(1); }
        @Override public void onNext(List<ByteBuffer> buffers) {
            for (var buffer : buffers) {
                if (buffer.remaining() > MAX_RESPONSE - bytes.size()) {
                    subscription.cancel();
                    result.completeExceptionally(IntelligenceFailure.invalid());
                    return;
                }
                byte[] chunk = new byte[buffer.remaining()];
                buffer.get(chunk);
                bytes.writeBytes(chunk);
            }
            subscription.request(1);
        }
        @Override public void onError(Throwable error) { result.completeExceptionally(error); }
        @Override public void onComplete() { result.complete(bytes.toByteArray()); }
    }
}
