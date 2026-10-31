package vn.haui.dx.apps.servicedesk.intelligence.context;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;

import vn.haui.dx.apps.servicedesk.intelligence.assessment.AssessmentPolicy;
import vn.haui.dx.apps.servicedesk.intelligence.duplicate.DuplicateDecisionEngine;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/** Báo cáo synthetic dùng đúng engine Java; không sao chép policy sang Python. */
class ContextQualityEvaluationTest {
    private final ObjectMapper json = new ObjectMapper();
    private final ContextPolicyCatalog policy = new ContextPolicyCatalog(json);
    private final ContextSlotExtractor extractor = new ContextSlotExtractor(policy, new AssessmentPolicy(json));
    private final EnrichmentSignalPlanner planner = new EnrichmentSignalPlanner(policy);
    private final DuplicateDecisionEngine engine = new DuplicateDecisionEngine();

    @Test
    void evaluatesOneHundredSyntheticRevisionsAndPairsWithDenominators() throws Exception {
        byte[] raw;
        try (var stream = getClass().getResourceAsStream("/apps/servicedesk/context-evaluation-v0.1.json")) {
            assertNotNull(stream);
            raw = stream.readAllBytes();
        }
        var dataset = json.readTree(raw);
        var records = new ArrayList<Map<String, Object>>();
        long slots = 0, spans = 0, missing = 0;
        for (var sample : dataset.path("cases")) {
            var query = snapshot(sample);
            var context = extractor.extract(query);
            var expected = sample.path("expected").fields();
            while (expected.hasNext()) {
                var entry = expected.next();
                assertEquals(entry.getValue(), json.valueToTree(context.value(entry.getKey())), sample.path("caseId").asText());
                slots++;
            }
            for (var slot : context.slots().values()) {
                for (var evidence : slot.evidence()) {
                    String source = query.sources().stream().filter(value -> value.sourceId().equals(evidence.sourceId()))
                            .findFirst().orElseThrow().text();
                    assertTrue(evidence.verifies(source));
                    spans++;
                }
            }
            var planned = planner.plan(context, "R10_DUPLICATE");
            for (var required : sample.path("missingSlots")) {
                assertTrue(planned.stream().anyMatch(signal -> signal.slot().equals(required.asText())));
                missing++;
            }
            var pair = sample.path("pair");
            var candidateSnapshot = new ContextSnapshot("synthetic", UUID.randomUUID(), 0,
                    List.of(new ContextSnapshot.Source("title", 0, pair.path("text").asText(), null, false)), "synthetic");
            var candidateContext = extractor.extract(candidateSnapshot);
            var decision = engine.decide(input(query, context, pair, true),
                    input(candidateSnapshot, candidateContext, pair, pair.path("open").asBoolean()));
            assertEquals(pair.path("expectedDecision").asText(), decision.decision().name(), sample.path("caseId").asText());
            records.add(Map.of("caseId", sample.path("caseId").asText(), "familyId", sample.path("familyId").asText(),
                    "decision", decision.decision().name(), "expectedDecision", pair.path("expectedDecision").asText()));
        }
        assertEquals(100, records.size());

        var report = new LinkedHashMap<String, Object>();
        report.put("datasetKind", "SYNTHETIC_ONLY");
        report.put("datasetDigest", ContextPolicyCatalog.digest(raw));
        report.put("policyBundleDigest", policy.bundleDigest());
        report.put("policyDigests", policy.digests());
        report.put("selectedModel", null);
        report.put("productionDecision", "NO_GO");
        report.put("reviewedGold", false);
        report.put("extraModelInvocations", 0);
        report.put("slotChecks", Map.of("passed", slots, "total", slots));
        report.put("literalEvidenceChecks", Map.of("passed", spans, "total", spans));
        report.put("missingSlotChecks", Map.of("passed", missing, "total", missing));
        report.put("pairChecks", Map.of("passed", records.size(), "total", records.size()));
        report.put("retrievalRecallAt20", "NOT_MEASURED_NO_GOLD_OR_RETRIEVAL_CORPUS");
        report.put("results", records);
        report.put("cpuPerformance", performance());
        report.put("javaVersion", System.getProperty("java.version"));
        report.put("os", System.getProperty("os.name"));
        report.put("availableProcessors", Runtime.getRuntime().availableProcessors());
        Path output = Path.of("target", "context-quality-evaluation.json");
        Files.createDirectories(output.getParent());
        json.writerWithDefaultPrettyPrinter().writeValue(output.toFile(), report);
    }

    private List<Map<String, Object>> performance() throws Exception {
        var snapshot = new ContextSnapshot("synthetic", UUID.randomUUID(), 0,
                List.of(new ContextSnapshot.Source("title", 0, "Máy 15 phòng 1304 không lên nguồn", null, false)), "synthetic");
        var context = extractor.extract(snapshot);
        var input = new DuplicateDecisionEngine.Input("synthetic", UUID.randomUUID(), Instant.EPOCH,
                context, "HARDWARE", true, true);
        var candidates = java.util.stream.IntStream.range(0, 50).mapToObj(index ->
                new DuplicateDecisionEngine.Input("synthetic", UUID.randomUUID(), Instant.EPOCH,
                        context, "HARDWARE", true, true)).toList();
        for (int index = 0; index < 200; index++) {
            extractor.extract(snapshot);
            candidates.forEach(candidate -> engine.decide(input, candidate));
        }
        var reports = new ArrayList<Map<String, Object>>();
        for (int concurrency : List.of(1, 2, 4)) {
            var pool = Executors.newFixedThreadPool(concurrency);
            try {
                var jobs = new ArrayList<java.util.concurrent.Future<long[]>>();
                for (int request = 0; request < 200; request++) {
                    jobs.add(pool.submit(() -> {
                        long start = System.nanoTime();
                        extractor.extract(snapshot);
                        long extracted = System.nanoTime();
                        for (var candidate : candidates) {
                            engine.decide(input, candidate);
                        }
                        return new long[] {extracted - start, System.nanoTime() - extracted};
                    }));
                }
                var extraction = new ArrayList<Long>();
                var decisions = new ArrayList<Long>();
                for (var job : jobs) {
                    long[] elapsed = job.get(30, TimeUnit.SECONDS);
                    extraction.add(elapsed[0]);
                    decisions.add(elapsed[1]);
                }
                extraction.sort(Long::compareTo);
                decisions.sort(Long::compareTo);
                reports.add(Map.of("concurrency", concurrency, "requests", jobs.size(), "degraded", 0,
                        "candidatesPerRequest", 50, "extractionMs", percentiles(extraction),
                        "decisionMs", percentiles(decisions), "includesDatabase", false));
            } finally {
                pool.shutdownNow();
            }
        }
        return reports;
    }

    private static Map<String, Double> percentiles(List<Long> times) {
        return Map.of("p50", times.get((int) Math.ceil(times.size() * 0.50) - 1) / 1_000_000.0,
                "p95", times.get((int) Math.ceil(times.size() * 0.95) - 1) / 1_000_000.0,
                "p99", times.get((int) Math.ceil(times.size() * 0.99) - 1) / 1_000_000.0);
    }

    private static ContextSnapshot snapshot(JsonNode sample) {
        var sources = new ArrayList<ContextSnapshot.Source>();
        sources.add(new ContextSnapshot.Source("title", 0, sample.path("title").asText(), null, false));
        int index = 0;
        for (var answer : sample.path("answers")) {
            sources.add(new ContextSnapshot.Source("clarification:00000000-0000-0000-0000-000000000001:" + index++, 1,
                    answer.path("value").asText(), answer.path("slot").asText(), answer.path("correction").asBoolean()));
        }
        return new ContextSnapshot("synthetic", UUID.randomUUID(), index == 0 ? 0 : 1, sources, "synthetic");
    }

    private static DuplicateDecisionEngine.Input input(
            ContextSnapshot snapshot, ContextProjection context, JsonNode pair, boolean open) {
        return new DuplicateDecisionEngine.Input("synthetic", snapshot.ticketId(), Instant.EPOCH, context,
                pair.path("category").asText(), pair.path("categoryVerified").asBoolean(), open);
    }
}
