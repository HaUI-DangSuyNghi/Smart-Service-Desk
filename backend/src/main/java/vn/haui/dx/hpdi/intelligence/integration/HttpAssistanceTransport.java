package vn.haui.dx.hpdi.intelligence.integration;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.stereotype.Component;

import vn.haui.dx.hpdi.intelligence.api.IntelligenceFailure;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Flow;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Component
public class HttpAssistanceTransport implements AssistanceTransport {
    private static final int MAX_REQUEST = 256 * 1024;
    private static final int MAX_RESPONSE = 128 * 1024;

    private final AssistanceConfiguration.Settings settings;
    private final ObjectMapper json;
    private final Semaphore slots = new Semaphore(4);
    private final HttpClient http =
            HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(1))
                    .followRedirects(HttpClient.Redirect.NEVER)
                    .proxy(ProxySelector.of(null))
                    .build();

    public HttpAssistanceTransport(AssistanceConfiguration.Settings settings, ObjectMapper json) {
        this.settings = settings;
        this.json =
                json.copy()
                        .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
                        .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    }

    private URI endpoint() {
        if (settings.baseUrl() == null || settings.baseUrl().isBlank()) {
            throw IntelligenceFailure.unavailable();
        }

        try {
            var base = URI.create(settings.baseUrl());

            if (!"http".equals(base.getScheme())
                    || !Set.of(
                                    "127.0.0.1",
                                    "localhost",
                                    "[::1]",
                                    "intelligence-assistance",
                                    "intelligence-cloud")
                            .contains(base.getHost())
                    || base.getPort() == 0
                    || base.getPort() > 65535
                    || base.getUserInfo() != null
                    || base.getQuery() != null
                    || base.getFragment() != null
                    || !(base.getPath().isEmpty() || "/".equals(base.getPath()))
                    || !settings.bearer().matches("[A-Za-z0-9._~+/-]{32,}=*")) {
                throw IntelligenceFailure.configuration();
            }

            return base.resolve("/internal/intelligence/v1/assistance");
        } catch (IllegalArgumentException | NullPointerException error) {
            throw IntelligenceFailure.configuration();
        }
    }

    @Override
    public JsonNode assist(JsonNode input) {
        var target = endpoint();
        byte[] body;

        try {
            body = json.writeValueAsBytes(input);
        } catch (IOException error) {
            throw IntelligenceFailure.configuration();
        }

        if (body.length > MAX_REQUEST) {
            throw IntelligenceFailure.configuration();
        }

        if (!slots.tryAcquire()) {
            throw IntelligenceFailure.unavailable();
        }

        try {
            return send(target, body);
        } finally {
            slots.release();
        }
    }

    private JsonNode send(URI target, byte[] body) {
        var request =
                HttpRequest.newBuilder(target)
                        .timeout(Duration.ofSeconds(15))
                        .header("Authorization", "Bearer " + settings.bearer())
                        .header("Content-Type", "application/json")
                        .header("Accept", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                        .build();
        var pending = http.sendAsync(request, info -> new LimitedBody());

        try {
            var response = pending.get(15, TimeUnit.SECONDS);
            int status = response.statusCode();

            if (status == 401 || status == 403) throw IntelligenceFailure.configuration();
            if (status == 504) throw IntelligenceFailure.timeout();
            if (status == 429 || status == 503) throw IntelligenceFailure.unavailable();
            if (status != 200) throw IntelligenceFailure.invalid();

            String mediaType =
                    response.headers()
                            .firstValue("Content-Type")
                            .orElse("")
                            .split(";", 2)[0]
                            .trim();

            if (!"application/json".equalsIgnoreCase(mediaType)
                    || response.headers().firstValue("Content-Encoding").isPresent()) {
                throw IntelligenceFailure.invalid();
            }

            try {
                return json.readTree(response.body());
            } catch (IOException error) {
                throw IntelligenceFailure.invalid();
            }
        } catch (TimeoutException error) {
            pending.cancel(true);
            throw IntelligenceFailure.timeout();
        } catch (InterruptedException error) {
            pending.cancel(true);
            Thread.currentThread().interrupt();
            throw IntelligenceFailure.unavailable();
        } catch (ExecutionException error) {
            if (error.getCause() instanceof HttpTimeoutException)
                throw IntelligenceFailure.timeout();
            if (error.getCause() instanceof IntelligenceFailure failure) throw failure;
            throw IntelligenceFailure.unavailable();
        }
    }

    @Override
    public void streamKnowledge(JsonNode input, java.util.function.Consumer<JsonNode> events) {
        byte[] body;
        try { body = json.writeValueAsBytes(input); }
        catch (IOException error) { throw IntelligenceFailure.configuration(); }
        if (body.length > 2_000_000) throw IntelligenceFailure.configuration();
        var target = endpoint().resolve("/internal/intelligence/v1/knowledge");
        if (!slots.tryAcquire()) throw IntelligenceFailure.unavailable();
        CompletableFuture<HttpResponse<Void>> pending = null;
        try {
            var request = HttpRequest.newBuilder(target).timeout(Duration.ofSeconds(40))
                .header("Authorization", "Bearer " + settings.bearer())
                .header("Content-Type", "application/json").header("Accept", "application/x-ndjson")
                .POST(HttpRequest.BodyPublishers.ofByteArray(body)).build();
            pending = http.sendAsync(request, info -> {
                if (info.statusCode() != 200 || !info.headers().firstValue("Content-Type").orElse("").startsWith("application/x-ndjson"))
                    throw IntelligenceFailure.unavailable();
                return new EventBody(events);
            });
            pending.get(40, TimeUnit.SECONDS);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw IntelligenceFailure.unavailable();
        } catch (TimeoutException error) { throw IntelligenceFailure.timeout(); }
        catch (ExecutionException error) { throw IntelligenceFailure.unavailable(); }
        finally {
            if (pending != null && !pending.isDone()) pending.cancel(true);
            slots.release();
        }
    }

    private final class EventBody implements HttpResponse.BodySubscriber<Void> {
        private final CompletableFuture<Void> result = new CompletableFuture<>();
        private final ByteArrayOutputStream line = new ByteArrayOutputStream();
        private final java.util.function.Consumer<JsonNode> events;
        private Flow.Subscription subscription;
        private int total;
        EventBody(java.util.function.Consumer<JsonNode> events) { this.events = events; }
        @Override public CompletionStage<Void> getBody() { return result; }
        @Override public void onSubscribe(Flow.Subscription incoming) {
            subscription = incoming;
            result.whenComplete((value, error) -> { if (error != null) incoming.cancel(); });
            incoming.request(1);
        }
        @Override public void onNext(List<ByteBuffer> buffers) {
            try {
                for (var buffer : buffers) while (buffer.hasRemaining()) {
                    byte next = buffer.get();
                    if (++total > 512000 || line.size() > 65536) throw IntelligenceFailure.invalid();
                    if (next == '\n') {
                        if (line.size() > 0) events.accept(json.readTree(line.toByteArray()));
                        line.reset();
                    } else line.write(next);
                }
                subscription.request(1);
            } catch (Exception error) { subscription.cancel(); result.completeExceptionally(error); }
        }
        @Override public void onError(Throwable error) { result.completeExceptionally(error); }
        @Override public void onComplete() {
            if (line.size() != 0) result.completeExceptionally(IntelligenceFailure.invalid());
            else result.complete(null);
        }
    }

    private static final class LimitedBody implements HttpResponse.BodySubscriber<byte[]> {
        private final CompletableFuture<byte[]> result = new CompletableFuture<>();
        private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        private Flow.Subscription subscription;

        @Override
        public CompletionStage<byte[]> getBody() {
            return result;
        }

        @Override
        public void onSubscribe(Flow.Subscription value) {
            subscription = value;
            value.request(1);
        }

        @Override
        public void onNext(List<ByteBuffer> buffers) {
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

        @Override
        public void onError(Throwable error) {
            result.completeExceptionally(error);
        }

        @Override
        public void onComplete() {
            result.complete(bytes.toByteArray());
        }
    }
}
