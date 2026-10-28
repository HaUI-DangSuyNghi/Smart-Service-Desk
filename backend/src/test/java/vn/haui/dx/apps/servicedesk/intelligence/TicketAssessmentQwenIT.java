package vn.haui.dx.apps.servicedesk.intelligence;

import static org.junit.jupiter.api.Assertions.*;

import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;

/** Opt-in real serving regression using owned synthetic text. */
class TicketAssessmentQwenIT extends QwenAssessmentTestSupport {
    @Test
    void realServingProducesVersionedReadOnlyAssessment() throws Exception {
        String route = createSyntheticRoute();
        long ticketsBefore = count("servicedesk_ticket"),
                tasksBefore = count("act_ru_task"),
                auditsBefore = count("audit_event");
        var latencies = new java.util.ArrayList<Double>();
        com.fasterxml.jackson.databind.JsonNode result = null;
        for (int attempt = 0; attempt < 10; attempt++) {
            long started = System.nanoTime();
            var reply = request(route);
            assertEquals(200, reply.getStatus());
            assertTrue(reply.getHeader("Cache-Control").contains("no-store"));
            latencies.add((System.nanoTime() - started) / 1_000_000.0);
            result = json.readTree(reply.getContentAsByteArray());
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
        }
        assertNotNull(result);
        var sorted = latencies.stream().sorted().toList();
        assertEquals(ticketsBefore, count("servicedesk_ticket"));
        assertEquals(tasksBefore, count("act_ru_task"));
        assertEquals(auditsBefore, count("audit_event"));
        var directory = Path.of("../.local/ai07/v19");
        Files.createDirectories(directory);
        Files.write(
                directory.resolve("qwen-e2e-" + UUID.randomUUID() + ".json"),
                json.writeValueAsBytes(
                        Map.of(
                                "purpose",
                                "OWNED_SYNTHETIC_REAL_SERVING_E2E_NOT_GOLD",
                                "latencyMs",
                                latencies,
                                "samples",
                                latencies.size(),
                                "p50Ms",
                                sorted.get(4),
                                "p95Ms",
                                sorted.get(9),
                                "p99Ms",
                                sorted.get(9),
                                "provenance",
                                result.path("provenance"),
                                "productionDecision",
                                "NO_GO")));
    }
}
