package vn.haui.dx.hpdi.intelligence.integration;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.stereotype.Component;

import vn.haui.dx.hpdi.intelligence.api.ClassificationInput;
import vn.haui.dx.hpdi.intelligence.api.ClassificationObservation;
import vn.haui.dx.hpdi.intelligence.api.IntelligenceFailure;

import java.io.ByteArrayOutputStream;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Flow;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Component
public class HttpClassificationTransport implements ClassificationTransport {
    private final ClassificationConfiguration.Settings settings;
    private final ObjectMapper json;
    private final Semaphore slot = new Semaphore(1);
    private final HttpClient http =
            HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(1))
                    .followRedirects(HttpClient.Redirect.NEVER)
                    .proxy(ProxySelector.of(null))
                    .build();
    private int failures;
    private long unavailableUntil;

    public HttpClassificationTransport(
            ClassificationConfiguration.Settings settings, ObjectMapper json) {
        this.settings = settings;
        this.json =
                json.copy()
                        .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
                        .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    }

    @Override
    public ClassificationObservation observe(ClassificationInput input) {
        if (!slot.tryAcquire()) {
            throw IntelligenceFailure.unavailable();
        }
        if (unavailableUntil != 0 && System.nanoTime() - unavailableUntil < 0) {
            slot.release();
            throw IntelligenceFailure.unavailable();
        }
        try {
            validateInput(input);
            String requestId = UUID.randomUUID().toString();
            String inputDigest =
                    HexFormat.of()
                            .formatHex(
                                    MessageDigest.getInstance("SHA-256")
                                            .digest(
                                                    json.writeValueAsBytes(
                                                            new TreeMap<>(input.fields()))));
            var payload = json.createObjectNode();
            payload.put("contractVersion", "classification-inference@0.1.0");
            payload.put("requestId", requestId).put("inputDigest", inputDigest);
            payload.put("modelDigest", input.modelDigest())
                    .put("policyDigest", input.policyDigest());
            payload.set("allowedClasses", json.valueToTree(input.allowedClasses()));
            payload.set("input", json.valueToTree(input.fields()));
            byte[] body = json.writeValueAsBytes(payload);
            require(body.length <= 16384);
            String bearer = settings.bearer();
            require(bearer != null && bearer.matches("[A-Za-z0-9._~+/-]{32,}=*"));
            var request =
                    HttpRequest.newBuilder(endpoint())
                            .timeout(Duration.ofSeconds(4))
                            .header("Authorization", "Bearer " + bearer)
                            .header("Content-Type", "application/json")
                            .header("Accept", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                            .build();
            var pending = http.sendAsync(request, info -> new BoundedBody());
            try {
                var response = pending.get(4, TimeUnit.SECONDS);
                if (response.statusCode() == 429 || response.statusCode() >= 500) {
                    throw IntelligenceFailure.unavailable();
                }
                require(response.statusCode() == 200);
                require(
                        response.headers()
                                .firstValue("Content-Type")
                                .orElse("")
                                .split(";", 2)[0]
                                .trim()
                                .equals("application/json"));
                var result =
                        validateResponse(
                                json.readTree(response.body()), input, requestId, inputDigest);
                failures = 0;
                unavailableUntil = 0;
                return result;
            } catch (TimeoutException failure) {
                pending.cancel(true);
                throw IntelligenceFailure.timeout();
            }
        } catch (IntelligenceFailure failure) {
            failed();
            throw failure;
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            failed();
            throw IntelligenceFailure.unavailable();
        } catch (ExecutionException failure) {
            failed();
            if (failure.getCause() instanceof HttpTimeoutException) {
                throw IntelligenceFailure.timeout();
            }
            if (failure.getCause() instanceof IntelligenceFailure contractFailure) {
                throw contractFailure;
            }
            throw IntelligenceFailure.unavailable();
        } catch (Exception failure) {
            failed();
            throw IntelligenceFailure.invalid();
        } finally {
            slot.release();
        }
    }

    private void failed() {
        if (++failures >= 3) {
            unavailableUntil = System.nanoTime() + Duration.ofSeconds(30).toNanos();
        }
    }

    private URI endpoint() {
        if (settings.baseUrl() == null || settings.baseUrl().isBlank()) {
            throw IntelligenceFailure.unavailable();
        }
        var base = URI.create(settings.baseUrl());
        require(
                "http".equals(base.getScheme())
                        && Set.of("127.0.0.1", "localhost", "[::1]", "intelligence-classification")
                                .contains(base.getHost()));
        require(base.getPort() != 0 && base.getPort() <= 65535 && base.getUserInfo() == null);
        require(
                base.getQuery() == null
                        && base.getFragment() == null
                        && (base.getPath().isEmpty() || "/".equals(base.getPath())));
        return base.resolve("/internal/intelligence/v1/classify");
    }

    static void validateInput(ClassificationInput input) {
        require(
                input != null
                        && input.modelDigest() != null
                        && input.modelDigest().matches("[0-9a-f]{64}"));
        require(input.policyDigest() != null && input.policyDigest().matches("[0-9a-f]{64}"));
        require(input.fields().size() >= 1 && input.fields().size() <= 8);
        require(
                input.fields().entrySet().stream()
                        .allMatch(
                                item ->
                                        item.getKey().matches("[a-z][a-z_]{0,31}")
                                                && item.getValue() != null));
        int length = input.fields().values().stream().mapToInt(String::length).sum();
        require(length > 0 && length <= 8192);
        require(input.allowedClasses().size() >= 2 && input.allowedClasses().size() <= 64);
        require(new HashSet<>(input.allowedClasses()).size() == input.allowedClasses().size());
        require(
                input.allowedClasses().stream()
                        .allMatch(value -> value.matches("[A-Za-z0-9_-]{1,64}")));
    }

    static ClassificationObservation validateResponse(
            JsonNode value, ClassificationInput input, String requestId, String inputDigest) {
        require(
                keys(value)
                        .equals(
                                Set.of(
                                        "contractVersion",
                                        "requestId",
                                        "inputDigest",
                                        "modelDigest",
                                        "policyDigest",
                                        "observation",
                                        "operationalAuthority")));
        require(text(value, "contractVersion").equals("classification-inference@0.1.0"));
        require(
                text(value, "requestId").equals(requestId)
                        && text(value, "inputDigest").equals(inputDigest));
        require(
                text(value, "modelDigest").equals(input.modelDigest())
                        && text(value, "policyDigest").equals(input.policyDigest()));
        require(
                value.path("operationalAuthority").isBoolean()
                        && !value.get("operationalAuthority").booleanValue());
        var observation = value.path("observation");
        require(
                keys(observation)
                        .equals(
                                Set.of(
                                        "decision",
                                        "classId",
                                        "evidenceStatus",
                                        "score",
                                        "scoreSemantics",
                                        "operationalAuthority")));
        String decision = text(observation, "decision");
        String classId = observation.path("classId").isNull() ? null : text(observation, "classId");
        require(
                "ABSTAIN".equals(decision) && classId == null
                        || "PROPOSE".equals(decision)
                                && classId != null
                                && input.allowedClasses().contains(classId));
        require(
                observation.path("score").isNull()
                        && text(observation, "scoreSemantics").equals("NOT_AVAILABLE"));
        require(text(observation, "evidenceStatus").equals("UNVERIFIED_BY_MODEL"));
        require(
                observation.path("operationalAuthority").isBoolean()
                        && !observation.get("operationalAuthority").booleanValue());
        return new ClassificationObservation(
                decision,
                classId,
                input.modelDigest(),
                input.policyDigest(),
                "UNVERIFIED_BY_MODEL",
                "NOT_AVAILABLE",
                false);
    }

    private static String text(JsonNode value, String key) {
        require(value.path(key).isTextual());
        return value.get(key).textValue();
    }

    private static Set<String> keys(JsonNode value) {
        require(value != null && value.isObject());
        var result = new HashSet<String>();
        value.fieldNames().forEachRemaining(result::add);
        return result;
    }

    private static void require(boolean condition) {
        if (!condition) {
            throw IntelligenceFailure.invalid();
        }
    }

    private static final class BoundedBody implements HttpResponse.BodySubscriber<byte[]> {
        private final CompletableFuture<byte[]> result = new CompletableFuture<>();
        private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        private Flow.Subscription subscription;

        public CompletionStage<byte[]> getBody() {
            return result;
        }

        public void onSubscribe(Flow.Subscription value) {
            subscription = value;
            value.request(1);
        }

        public void onNext(List<ByteBuffer> buffers) {
            for (var buffer : buffers) {
                if (buffer.remaining() > 16384 - bytes.size()) {
                    subscription.cancel();
                    result.completeExceptionally(IntelligenceFailure.invalid());
                    return;
                }
                byte[] block = new byte[buffer.remaining()];
                buffer.get(block);
                bytes.writeBytes(block);
            }
            subscription.request(1);
        }

        public void onError(Throwable failure) {
            result.completeExceptionally(failure);
        }

        public void onComplete() {
            result.complete(bytes.toByteArray());
        }
    }
}
