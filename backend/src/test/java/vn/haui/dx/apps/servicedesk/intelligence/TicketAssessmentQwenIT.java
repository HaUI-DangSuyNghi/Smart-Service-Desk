package vn.haui.dx.apps.servicedesk.intelligence;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import vn.haui.dx.apps.servicedesk.intelligence.assessment.AssessmentPolicy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;

/**
 * Opt-in: real Java -> private Python -> Qwen; owned synthetic input only, no release authority.
 */
@SpringBootTest(
        properties = {
            "serviceDesk.classifier.mode=ASSISTED",
            "serviceDesk.classifier.provider=QWEN_SYNTHETIC_E2E",
            "serviceDesk.assessment.enabled=true"
        })
@AutoConfigureMockMvc
class TicketAssessmentQwenIT {
    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry properties) {
        if (!"true".equals(System.getenv("AI07_REAL_QWEN_E2E"))) {
            throw new IllegalStateException("Explicit synthetic Qwen E2E opt-in is required");
        }
        properties.add("spring.datasource.url", () -> required("FILE_TEST_JDBC_URL"));
        properties.add("spring.datasource.username", () -> "workflow");
        properties.add("spring.datasource.password", () -> required("FILE_TEST_DB_PASSWORD"));
        properties.add(
                "serviceDesk.classifier.model-digest", () -> required("AI07_QWEN_MODEL_DIGEST"));
        properties.add(
                "serviceDesk.classifier.policy-digest", () -> required("AI07_QWEN_POLICY_DIGEST"));
        properties.add(
                "intelligence.classification.base-url", () -> required("AI07_QWEN_BASE_URL"));
        properties.add("intelligence.classification.token", () -> required("AI07_QWEN_TOKEN"));
        properties.add(
                "serviceDesk.assessment.policy-bundle-digest",
                () -> new AssessmentPolicy(new ObjectMapper()).bundleDigest());
        properties.add(
                "spring.security.oauth2.resourceserver.jwt.jwk-set-uri",
                () -> "http://127.0.0.1:1/jwks");
        properties.add(
                "dx.storage.root",
                () ->
                        Path.of(
                                        System.getProperty("java.io.tmpdir"),
                                        "ai07-qwen-e2e-" + UUID.randomUUID())
                                .toString());
    }

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;
    @Autowired private JdbcTemplate jdbc;
    // Test-only processing authorization for project-owned synthetic text. Never an owner grant.
    @MockBean private ClassifierProcessingGate processing;

    @Test
    void realServingProducesVersionedReadOnlyAssessment() throws Exception {
        String workspace = "qwen-synthetic-e2e";
        String base = "/api/apps/servicedesk/v1/workspaces/" + workspace + "/tickets";
        var actor =
                jwt().jwt(
                                token ->
                                        token.subject("synthetic-owner")
                                                .claim(
                                                        "groups",
                                                        java.util.List.of(
                                                                "/workspaces/"
                                                                        + workspace
                                                                        + "/requester")));
        when(processing.verify(workspace)).thenReturn("SYNTHETIC_TEST_ONLY");
        String created =
                mvc.perform(
                                post(base)
                                        .with(actor)
                                        .contentType("application/json")
                                        .content(
                                                json.writeValueAsBytes(
                                                        Map.of(
                                                                "clientRequestId",
                                                                UUID.randomUUID(),
                                                                "title",
                                                                "không kết nối được Wi-Fi",
                                                                "description",
                                                                "cả phòng bị ảnh hưởng; thi sau 15"
                                                                        + " phút"))))
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        String route =
                base + "/" + json.readTree(created).path("id").asText() + "/assistant/assessment";
        long ticketsBefore = count("servicedesk_ticket"),
                tasksBefore = count("act_ru_task"),
                auditsBefore = count("audit_event");
        var latencies = new java.util.ArrayList<Double>();
        com.fasterxml.jackson.databind.JsonNode result = null;
        for (int attempt = 0; attempt < 10; attempt++) {
            long started = System.nanoTime();
            var reply =
                    mvc.perform(
                                    post(route)
                                            .with(actor)
                                            .contentType("application/json")
                                            .content(
                                                    json.writeValueAsBytes(
                                                            Map.of(
                                                                    "contractVersion",
                                                                    TicketAssessmentView.VERSION))))
                            .andExpect(status().isOk())
                            .andExpect(
                                    header().string(
                                                    "Cache-Control",
                                                    org.hamcrest.Matchers.containsString(
                                                            "no-store")))
                            .andExpect(jsonPath("$.status").value("REVIEW_REQUIRED"))
                            .andExpect(jsonPath("$.disposition.operationalAuthority").value(false))
                            .andReturn()
                            .getResponse();
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

    private long count(String table) {
        return jdbc.queryForObject("select count(*) from " + table, Long.class);
    }

    private static String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing " + name);
        }
        return value;
    }
}
