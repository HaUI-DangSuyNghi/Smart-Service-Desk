package vn.haui.dx.apps.servicedesk.intelligence;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;

import vn.haui.dx.apps.servicedesk.intelligence.taxonomy.SubcategoryResolver;
import vn.haui.dx.apps.servicedesk.intelligence.taxonomy.TaxonomyCatalog;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

class SubcategoryBenchmarkTest {
    @Test
    void frozenMultiSymptomCohortsKeepCoverageAndHonestAbstention() throws Exception {
        var json = new ObjectMapper();
        var resolver = new SubcategoryResolver(new TaxonomyCatalog(json));
        var cohorts = new TreeMap<String, Map<String, Integer>>();
        try (var stream =
                getClass()
                        .getResourceAsStream(
                                "/apps/servicedesk/intelligence/multi-symptom-v0.1.json")) {
            assertNotNull(stream);
            var fixture = json.readTree(stream);
            for (var row : fixture.path("cases")) {
                var fields =
                        Map.of(
                                "title",
                                row.path("title").asText(),
                                "description",
                                row.path("description").asText());
                var result = resolver.resolve(row.path("parent").asText(), fields);
                assertEquals(
                        row.path("expectedChild").textValue(),
                        result.child(),
                        row.path("id").asText());
                assertEquals(
                        row.path("expectedStatus").asText(),
                        result.status(),
                        row.path("id").asText());
                var stats =
                        cohorts.computeIfAbsent(
                                row.path("cohort").asText(), ignored -> new TreeMap<>());
                stats.merge("cases", 1, Integer::sum);
                stats.merge(result.child() == null ? "abstained" : "proposed", 1, Integer::sum);
                result.evidence()
                        .forEach(span -> assertTrue(span.verifies(fields.get(span.field()))));
                assertEquals(result, resolver.resolve(row.path("parent").asText(), fields));
            }
            var report = new LinkedHashMap<String, Object>();
            report.put("purpose", fixture.path("purpose").asText());
            report.put("cohorts", cohorts);
            report.put("parentInput", "SYNTHETIC_MODEL_PARENT_NOT_REAL_QWEN_PREDICTION");
            report.put("productionDecision", "NO_GO");
            Path path =
                    Path.of(
                            "../.local/ai07/review-quality/subtag-benchmark-"
                                    + java.util.UUID.randomUUID()
                                    + ".json");
            Files.createDirectories(path.getParent());
            Files.write(path, json.writeValueAsBytes(report));
        }
    }
}
