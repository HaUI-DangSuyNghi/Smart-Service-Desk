package vn.haui.dx.apps.servicedesk.intelligence;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;

/** Explicit opt-in load measurement, not a deployment or quality promotion test. */
class TicketAssessmentQwenLoadIT extends QwenAssessmentTestSupport {
    private record Attempt(
            double latencyMs,
            int httpStatus,
            boolean accepted,
            String outcome,
            JsonNode provenance) {}

    @Test
    void recordsFirstInferenceConcurrencyAndRejectedAttemptsSeparately() throws Exception {
        int samples = bounded("AI07_LOAD_SAMPLES", 100, 20, 2000);
        String route = createSyntheticRoute();
        long ticketCount = count("servicedesk_ticket"),
                taskCount = count("act_ru_task"),
                auditCount = count("audit_event");
        Attempt first = attempt(route);
        assertTrue(first.accepted(), "First real inference must succeed before measuring capacity");
        String expectedPolicy = System.getenv("AI07_ASSESSMENT_POLICY_DIGEST");
        if (expectedPolicy != null) {
            assertEquals(
                    expectedPolicy, first.provenance().path("assessmentPolicyDigest").asText());
        }
        // Provider warmup is explicit and excluded from throughput/percentiles.
        for (int i = 0; i < 3; i++) {
            assertTrue(attempt(route).accepted());
        }
        var phases = new ArrayList<Map<String, Object>>();
        for (int concurrency : List.of(1, 2, 4)) {
            var workers = Executors.newFixedThreadPool(concurrency);
            var attempts = new ArrayList<Attempt>();
            long started = System.nanoTime();
            try {
                for (int completed = 0; completed < samples; ) {
                    int batchSize = Math.min(concurrency, samples - completed);
                    var barrier = new CyclicBarrier(batchSize);
                    var jobs = new ArrayList<Callable<Attempt>>();
                    for (int i = 0; i < batchSize; i++) {
                        jobs.add(
                                () -> {
                                    barrier.await();
                                    return attempt(route);
                                });
                    }
                    for (var pending : workers.invokeAll(jobs)) {
                        attempts.add(pending.get());
                    }
                    completed += batchSize;
                }
            } finally {
                workers.shutdownNow();
            }
            var phase = new LinkedHashMap<String, Object>();
            var accepted = attempts.stream().filter(Attempt::accepted).toList();
            phase.put("concurrency", concurrency);
            phase.put("attempts", attempts.size());
            phase.put("accepted", accepted.size());
            phase.put("degradedOrRejectedRate", 1.0 - (double) accepted.size() / attempts.size());
            double elapsedSeconds = (System.nanoTime() - started) / 1_000_000_000.0;
            phase.put("elapsedSeconds", elapsedSeconds);
            phase.put("acceptedPerSecond", accepted.size() / elapsedSeconds);
            phase.put("allAttemptLatency", percentiles(attempts));
            phase.put("acceptedLatency", percentiles(accepted));
            var outcomes = new java.util.TreeMap<String, Long>();
            attempts.forEach(
                    attempt ->
                            outcomes.merge(
                                    attempt.httpStatus() + ":" + attempt.outcome(), 1L, Long::sum));
            phase.put("outcomes", outcomes);
            phases.add(phase);
        }
        assertEquals(ticketCount, count("servicedesk_ticket"));
        assertEquals(taskCount, count("act_ru_task"));
        assertEquals(auditCount, count("audit_event"));
        var report = new LinkedHashMap<String, Object>();
        report.put("purpose", "OWNED_SYNTHETIC_ASSESSMENT_LOAD_NOT_GOLD");
        report.put(
                "scope",
                "MockMvc + PostgreSQL + private HTTP + real Qwen; no gateway/OIDC latency");
        report.put("firstInferenceMs", first.latencyMs());
        report.put("providerReadinessObservedMs", System.getenv("AI07_COLD_READY_MS"));
        report.put("provenance", first.provenance());
        report.put("sourceFingerprint", System.getenv("AI07_SOURCE_FINGERPRINT"));
        report.put("phases", phases);
        report.put("businessWrites", 0);
        report.put("sloDecision", "NOT_EVALUATED_NO_APPROVED_SLO");
        report.put("productionDecision", "NO_GO");
        String runId =
                System.getenv().getOrDefault("AI07_BENCHMARK_RUN_ID", UUID.randomUUID().toString());
        if (!runId.matches("[A-Za-z0-9-]{1,64}")) {
            throw new IllegalArgumentException("Invalid benchmark run ID");
        }
        report.put("runId", runId);
        report.put("coldCycle", System.getenv("AI07_COLD_CYCLE"));
        Path directory =
                Path.of("../.local/ai07/review-performance", runId).toAbsolutePath().normalize();
        Files.createDirectories(directory);
        Files.write(
                directory.resolve("load-" + UUID.randomUUID() + ".json"),
                json.writeValueAsBytes(report));
    }

    private Attempt attempt(String route) throws Exception {
        long started = System.nanoTime();
        var response = request(route);
        double latency = (System.nanoTime() - started) / 1_000_000.0;
        var result = json.readTree(response.getContentAsByteArray());
        boolean accepted =
                response.getStatus() == 200
                        && "REVIEW_REQUIRED".equals(result.path("status").asText());
        if (response.getStatus() == 200) {
            try (var stream =
                    getClass()
                            .getResourceAsStream(
                                    "/servicedesk-contract/ticket-assessment-v0.2.schema.json")) {
                assertNotNull(stream);
                var schema =
                        JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012)
                                .getSchema(json.readTree(stream));
                assertTrue(schema.validate(result).isEmpty());
            }
            assertTrue(response.getHeader("Cache-Control").contains("no-store"));
            assertFalse(result.path("disposition").path("operationalAuthority").asBoolean());
        }
        String outcome =
                response.getStatus() == 200
                        ? result.path("status").asText()
                        : result.path("code").asText();
        if ("DEGRADED".equals(outcome)) {
            for (var reason : result.path("disposition").path("reasons")) {
                if (reason.asText().startsWith("INTELLIGENCE_")) {
                    outcome += ":" + reason.asText();
                    break;
                }
            }
        }
        return new Attempt(
                latency, response.getStatus(), accepted, outcome, result.path("provenance"));
    }

    private static Map<String, Object> percentiles(List<Attempt> attempts) {
        var sorted = attempts.stream().map(Attempt::latencyMs).sorted().toList();
        var result = new LinkedHashMap<String, Object>();
        for (int p : List.of(50, 95, 99)) {
            result.put(
                    "p" + p + "Ms",
                    sorted.isEmpty()
                            ? null
                            : sorted.get((int) Math.ceil(p * sorted.size() / 100.0) - 1));
        }
        return result;
    }

    private static int bounded(String name, int fallback, int minimum, int maximum) {
        String raw = System.getenv(name);
        int value = raw == null ? fallback : Integer.parseInt(raw);
        if (value < minimum || value > maximum) {
            throw new IllegalArgumentException("Invalid " + name);
        }
        return value;
    }
}
