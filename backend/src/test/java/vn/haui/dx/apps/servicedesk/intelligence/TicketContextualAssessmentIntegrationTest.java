package vn.haui.dx.apps.servicedesk.intelligence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import vn.haui.dx.apps.servicedesk.clarification.ClarificationRequest;
import vn.haui.dx.apps.servicedesk.clarification.TicketClarificationService;
import vn.haui.dx.apps.servicedesk.intelligence.assessment.AssessmentPolicy;
import vn.haui.dx.apps.servicedesk.intelligence.context.ContextPolicyCatalog;
import vn.haui.dx.apps.servicedesk.intelligence.context.ContextProcessingGate;
import vn.haui.dx.hpdi.intelligence.api.ClassificationObservation;
import vn.haui.dx.hpdi.intelligence.api.IntelligenceFailure;
import vn.haui.dx.hpdi.intelligence.integration.ClassificationTransport;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"serviceDesk.context.enabled=true", "serviceDesk.context.mode=ASSISTED",
        "serviceDesk.enrichment.enabled=true", "serviceDesk.contextual-assessment.enabled=true",
        "serviceDesk.classifier.mode=ASSISTED", "serviceDesk.classifier.provider=SYNTHETIC_CONTEXTUAL_TEST",
        "serviceDesk.classifier.model-digest=aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
        "serviceDesk.classifier.policy-digest=bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"})
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "FILE_TEST_JDBC_URL", matches = ".+/file_storage")
class TicketContextualAssessmentIntegrationTest {
    private static final String BASE = "/api/apps/servicedesk/v1/workspaces/contextual-a/tickets";

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", () -> System.getenv("FILE_TEST_JDBC_URL"));
        properties.add("spring.datasource.username", () -> "workflow");
        properties.add("spring.datasource.password", () -> System.getenv("FILE_TEST_DB_PASSWORD"));
        properties.add("dx.storage.root", () -> Path.of(System.getProperty("java.io.tmpdir"), "contextual-" + UUID.randomUUID()).toString());
        properties.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri", () -> "http://127.0.0.1:1/jwks");
        properties.add("serviceDesk.contextual-assessment.policy-digest", () -> {
            var json = new ObjectMapper();
            return new TicketAssessmentInputAssembler(json, new AssessmentPolicy(json), new ContextPolicyCatalog(json),
                    new ClassifierConfiguration.Settings(ClassifierConfiguration.Mode.ASSISTED, "SYNTHETIC_CONTEXTUAL_TEST",
                            "a".repeat(64), "b".repeat(64), "", "")).policyDigest();
        });
    }

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private TicketClarificationService clarifications;
    @MockBean private ClassificationTransport transport;
    @MockBean private ContextProcessingGate processing;
    @MockBean private ClassifierProcessingGate modelRights;

    @BeforeEach
    void grant() {
        when(processing.verifyReadiness(eq("contextual-a"), anyString(), anyString())).thenReturn("synthetic-context-grant");
        when(processing.verifyContextual(eq("contextual-a"), anyString(), anyString(), anyString())).thenReturn("synthetic-context-grant");
        when(modelRights.verifyAssessment(eq("contextual-a"), anyString())).thenReturn("synthetic-model-grant");
        when(transport.observe(any())).thenAnswer(call -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            return vote();
        });
    }

    @Test
    void reassessmentConsumesTypedSupplementAndBindsFreshRevision() throws Exception {
        var ticket = create();
        var initial = assess(ticket, 200);
        assertTrue(initial.path("ai07").path("impact").isNull());
        append(ticket, 0, List.of(new ClarificationRequest.Answer("affectedScope", "WHOLE_ROOM", false),
                new ClarificationRequest.Answer("workBlocked", "có", false)));
        var refreshed = assess(ticket, 200);
        assertEquals(1, refreshed.path("contextRevision").asLong());
        assertNotEquals(initial.path("contextDigest"), refreshed.path("contextDigest"));
        assertEquals("I3", refreshed.path("ai07").path("impact").asText());
        assertEquals("U3", refreshed.path("ai07").path("urgency").asText());
        assertTrue(refreshed.path("ai07").path("confidence").isNull());
        verify(transport, times(2)).observe(argThat(input -> input.fields().keySet().equals(java.util.Set.of("title", "description"))));
        verify(transport).observe(argThat(input -> input.fields().get("description").contains("WHOLE_ROOM")));
        assertEquals("MODEL_REUSED_MATERIAL", assess(ticket, 200).path("assessmentStatus").asText());
        verify(transport, times(2)).observe(any());
    }

    @Test
    void roomOnlyUpdateReusesMaterialCategoryButRebindsContext() throws Exception {
        var ticket = create();
        assess(ticket, 200);
        append(ticket, 0, List.of(new ClarificationRequest.Answer("location.room", "1304", false)));
        var result = assess(ticket, 200);
        assertEquals(1, result.path("contextRevision").asLong());
        assertEquals("MODEL_REUSED_MATERIAL", result.path("assessmentStatus").asText());
        verify(transport, times(1)).observe(any());
    }

    @Test
    void appendDuringInferenceDiscardsEntireStaleResponse() throws Exception {
        var ticket = create();
        when(transport.observe(any())).thenAnswer(call -> {
            clarifications.append("contextual-a", id(ticket), new ClarificationRequest("ticket-clarification@0.1.0",
                    UUID.randomUUID(), 0L, List.of(new ClarificationRequest.Answer("workBlocked", "có", false))));
            return vote();
        });
        assess(ticket, 409);
        assertEquals(1L, jdbc.queryForObject("select context_revision from servicedesk_ticket where id=?", Long.class, id(ticket)));
    }

    @Test
    void grantDriftDiscardsModelResult() throws Exception {
        var ticket = create();
        when(processing.verifyContextual(eq("contextual-a"), anyString(), anyString(), anyString()))
                .thenReturn("before", "before", "after");
        assess(ticket, 409);
    }

    @Test
    void providerFailureDegradesOnceAndWorkerRoutesToManual() throws Exception {
        var ticket = create();
        when(transport.observe(any())).thenThrow(IntelligenceFailure.unavailable());
        var degraded = assess(ticket, 200);
        assertEquals("DEGRADED", degraded.path("assessmentStatus").asText());
        assertTrue(degraded.path("ai07").path("category").isNull());
        assess(ticket, 200);
        verify(transport, times(1)).observe(any());
        // Command sau debounce; không sleep để làm nguồn sự thật của job.
        jdbc.update("update servicedesk_enrichment_outbox set available_at=current_timestamp where ticket_id=?", id(ticket));
        var response = mvc.perform(post(BASE + "/" + id(ticket) + "/enrichment-state/evaluate")
                        .with(as("owner")).contentType("application/json")
                        .content("{\"contractVersion\":\"ticket-enrichment-lifecycle@0.1.0\"}"))
                .andExpect(status().isOk()).andReturn();
        assertEquals("MANUAL_REVIEW", json.readTree(response.getResponse().getContentAsString()).path("status").asText());
        verify(transport, times(1)).observe(any());
    }

    @Test
    void untrustedBodyCannotInjectSourceOrRevision() throws Exception {
        var ticket = create();
        mvc.perform(post(route(ticket)).with(as("owner")).contentType("application/json")
                        .content("{\"contractVersion\":\"ticket-assessment-contextual@0.1.0\",\"description\":\"fake\",\"contextRevision\":99}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post(route(ticket)).with(as("other")).contentType("application/json")
                        .content("{\"contractVersion\":\"ticket-assessment-contextual@0.1.0\"}"))
                .andExpect(status().isNotFound());
        verifyNoInteractions(transport);
    }

    private JsonNode create() throws Exception {
        return json.readTree(mvc.perform(post(BASE).with(as("owner")).contentType("application/json")
                        .content(json.writeValueAsString(Map.of("clientRequestId", UUID.randomUUID(), "title", "Máy tính bị lỗi",
                                "description", "Nhờ hỗ trợ kiểm tra thiết bị."))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
    }

    private void append(JsonNode ticket, long revision, List<ClarificationRequest.Answer> answers) throws Exception {
        mvc.perform(post(BASE + "/" + id(ticket) + "/clarifications").with(as("owner")).contentType("application/json")
                        .content(json.writeValueAsString(new ClarificationRequest("ticket-clarification@0.1.0", UUID.randomUUID(), revision, answers))))
                .andExpect(status().isCreated());
    }

    private JsonNode assess(JsonNode ticket, int expected) throws Exception {
        var value = json.readTree(mvc.perform(post(route(ticket)).with(as("owner")).contentType("application/json")
                        .content("{\"contractVersion\":\"ticket-assessment-contextual@0.1.0\"}"))
                .andExpect(status().is(expected)).andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
        if (expected == 200) {
            vn.haui.dx.apps.servicedesk.intelligence.context.ContextSchemaAssertions.verify(
                    json, "ticket-assessment-contextual-v0.1.schema.json", value);
        }
        return value;
    }

    private String route(JsonNode ticket) { return BASE + "/" + id(ticket) + "/assistant/assessment-contextual"; }
    private UUID id(JsonNode ticket) { return UUID.fromString(ticket.path("id").asText()); }
    private ClassificationObservation vote() { return new ClassificationObservation("PROPOSE", "HARDWARE", "a".repeat(64), "b".repeat(64), "UNVERIFIED_BY_MODEL", "NOT_AVAILABLE", false); }
    private static RequestPostProcessor as(String subject) {
        return jwt().jwt(token -> token.subject(subject).claim("groups", List.of("/workspaces/contextual-a/requester")));
    }
}
