package vn.haui.dx.apps.servicedesk.intelligence.enrichment;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import vn.haui.dx.apps.servicedesk.intelligence.assessment.AssessmentPolicy;
import vn.haui.dx.apps.servicedesk.intelligence.assessment.ProblemAssessor;
import vn.haui.dx.apps.servicedesk.intelligence.context.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class EnrichmentReadinessEvaluationTest {
    @Test
    void frozenSyntheticCohortHasNoWrongGlobalBlockOrMissedSafetyBypass() throws Exception {
        var json = new ObjectMapper();
        var assessment = new AssessmentPolicy(json);
        var contextPolicy = new ContextPolicyCatalog(json);
        var extractor = new ContextSlotExtractor(contextPolicy, assessment);
        var policy = new ContextReadinessPolicy(json, new ProblemAssessor(assessment));
        byte[] raw;
        try (var stream = getClass().getResourceAsStream("/apps/servicedesk/readiness-cases-v0.1.json")) {
            assertNotNull(stream);
            raw = stream.readAllBytes();
        }
        var dataset = json.readTree(raw);
        var results = new ArrayList<Map<String, Object>>();
        var latencies = new ArrayList<Long>();
        int safety = 0, continueCases = 0, evidenceCount = 0;
        for (var sample : dataset.path("cases")) {
            var snapshot = new ContextSnapshot("synthetic", UUID.randomUUID(), 0, List.of(
                    new ContextSnapshot.Source("title", 0, sample.path("text").asText(), null, false)), "a".repeat(64));
            long started = System.nanoTime();
            var context = extractor.extract(snapshot);
            var result = policy.evaluate(snapshot, context, sample.path("rounds").asInt());
            latencies.add(System.nanoTime() - started);
            assertEquals(sample.path("expected").asText(), result.overall().name(), sample.path("caseId").asText());
            assertFalse(result.workflowGateApplied());
            assertFalse(result.operationalAuthority());
            assertTrue(result.questions().size() <= 3);
            if (sample.path("safety").asBoolean()) {
                safety++;
                assertEquals(ContextReadinessView.State.MANUAL_REVIEW_REQUIRED, result.overall());
            }
            if (List.of("READINESS_OK", "ENRICHMENT_OPTIONAL").contains(sample.path("expected").asText())) {
                continueCases++;
                assertEquals(ContextReadinessView.State.READINESS_OK, result.tasks().get(0).state());
            }
            for (var slot : context.slots().values()) {
                for (var span : slot.evidence()) {
                    assertTrue(span.verifies(snapshot.sources().get(0).text()));
                    evidenceCount++;
                }
            }
            results.add(Map.of("caseId", sample.path("caseId").asText(), "familyId", sample.path("familyId").asText(),
                    "observed", result.overall().name(), "expected", sample.path("expected").asText()));
        }
        assertEquals(100, results.size());
        latencies.sort(Long::compareTo);
        var report = new java.util.LinkedHashMap<String, Object>();
        report.put("datasetKind", "SYNTHETIC_ONLY");
        report.put("datasetDigest", ContextPolicyCatalog.digest(raw));
        report.put("policyDigest", policy.digest());
        report.put("cases", results);
        report.put("safetyBypass", Map.of("passed", safety, "total", safety));
        report.put("unblockedAssessment", Map.of("passed", continueCases, "total", continueCases));
        report.put("literalEvidence", Map.of("passed", evidenceCount, "total", evidenceCount));
        report.put("wrongGlobalBlocks", 0);
        report.put("workflowGateApplied", false);
        report.put("cpuP95Ms", latencies.get(94) / 1_000_000.0);
        report.put("includesDatabaseOrModel", false);
        report.put("productionDecision", "NO_GO");
        report.put("modelBenchmark", "NOT_PERFORMED_AUGMENTED_MODEL_VALIDATION_REQUIRED");
        var output = Path.of("target", "enrichment-readiness-evaluation.json");
        Files.createDirectories(output.getParent());
        json.writerWithDefaultPrettyPrinter().writeValue(output.toFile(), report);
    }
}
