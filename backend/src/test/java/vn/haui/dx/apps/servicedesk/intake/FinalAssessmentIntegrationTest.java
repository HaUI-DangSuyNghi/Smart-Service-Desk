package vn.haui.dx.apps.servicedesk.intake;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import vn.haui.dx.apps.servicedesk.clarification.*;
import vn.haui.dx.apps.servicedesk.intelligence.*;
import vn.haui.dx.apps.servicedesk.intelligence.assessment.AssessmentPolicy;
import vn.haui.dx.apps.servicedesk.intelligence.context.*;
import vn.haui.dx.hpdi.common.api.CapabilityException;
import vn.haui.dx.hpdi.intelligence.api.*;
import vn.haui.dx.hpdi.intelligence.integration.ClassificationTransport;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"serviceDesk.intake.new-tickets=true", "serviceDesk.context.enabled=true",
        "serviceDesk.context.mode=ASSISTED", "serviceDesk.contextual-assessment.enabled=true",
        "serviceDesk.classifier.mode=ASSISTED", "serviceDesk.classifier.provider=SYNTHETIC_CONTEXTUAL_TEST",
        "serviceDesk.classifier.model-digest=aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
        "serviceDesk.classifier.policy-digest=bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
        "service-desk.background.scheduled=false", "service-desk.background.workspaces=d2-model"})
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "FILE_TEST_JDBC_URL", matches = ".+/file_storage")
class FinalAssessmentIntegrationTest {
    private static final String BASE = "/api/apps/servicedesk/v1/workspaces/d2-model/tickets";
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired BackgroundTicketAssessment worker;
    @Autowired TicketClarificationService clarifications;
    @MockBean ContextProcessingGate rights;
    @MockBean ClassifierProcessingGate modelRights;
    @MockBean ClassificationTransport transport;
    @MockBean BackgroundProcessing background;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> System.getenv("FILE_TEST_JDBC_URL"));
        registry.add("spring.datasource.username", () -> "workflow");
        registry.add("spring.datasource.password", () -> System.getenv("FILE_TEST_DB_PASSWORD"));
        registry.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri", () -> "http://127.0.0.1:1/jwks");
        registry.add("serviceDesk.contextual-assessment.policy-digest", () -> {
            var mapper = new ObjectMapper();
            return new TicketAssessmentInputAssembler(mapper, new AssessmentPolicy(mapper), new ContextPolicyCatalog(mapper),
                    new ClassifierConfiguration.Settings(ClassifierConfiguration.Mode.ASSISTED, "SYNTHETIC_CONTEXTUAL_TEST",
                            "a".repeat(64), "b".repeat(64), "", "")).policyDigest();
        });
    }

    @BeforeEach
    void configureSyntheticPrincipalAndIndependentGrants() {
        when(rights.verify(anyString(), anyString())).thenReturn("synthetic-context-grant");
        when(rights.verifyReadiness(anyString(), anyString(), anyString())).thenReturn("synthetic-context-grant");
        when(rights.verifyContextual(anyString(), anyString(), anyString(), anyString())).thenReturn("synthetic-context-grant");
        when(modelRights.verifyAssessment(anyString(), anyString())).thenReturn("synthetic-model-grant");
        doAnswer(call -> {
            runAs("synthetic-worker", "assessment-worker", call.getArgument(1));
            return null;
        }).when(background).run(eq("d2-model"), any(Runnable.class));
        when(transport.observe(any())).thenAnswer(call -> {
            assertFalse(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive());
            return vote();
        });
    }

    @Test
    void latestSnapshotIsPersistedAndBrowserReadsNeverInferAgain() throws Exception {
        var ticket = create("Máy 15 phòng 1304 không lên nguồn");
        assertFalse(worker.processOne(), "Legacy job must wait for seal");
        assertEquals("FINAL_ASSESSMENT_QUEUED", evaluate(ticket).path("status").asText());
        assertTrue(worker.processOne());
        for (int index = 0; index < 4; index++) {
            var result = read(ticket);
            assertEquals("REVIEW_RELEASED", result.path("status").asText());
            assertEquals("HARDWARE", result.path("finalAssessment").path("ai07").path("category").asText());
            assertTrue(result.path("finalAssessment").path("ai07").path("confidence").isNull());
        }
        assertFalse(worker.processOne());
        verify(transport, times(1)).observe(any());
    }

    @Test
    void requiredEnrichmentDoesNotQueueModel() throws Exception {
        var ticket = create("Xin hỗ trợ, chưa rõ lỗi");
        assertEquals("NEEDS_ENRICHMENT", evaluate(ticket).path("status").asText());
        assertFalse(worker.processOne());
        verifyNoInteractions(transport);
    }

    @Test
    void clarificationIsIncludedBeforeTheFirstFinalCall() throws Exception {
        var ticket = create("Xin hỗ trợ, chưa rõ lỗi");
        evaluate(ticket);
        append(ticket, 0, List.of(new ClarificationRequest.Answer("device.type", "PC", false),
                new ClarificationRequest.Answer("device.identifier", "15", false),
                new ClarificationRequest.Answer("location.room", "1304", false),
                new ClarificationRequest.Answer("symptomCodes", "không lên nguồn", false),
                new ClarificationRequest.Answer("workBlocked", "có", false)));
        evaluate(ticket);
        assertTrue(worker.processOne());
        assertEquals(1, read(ticket).path("finalAssessment").path("contextRevision").asLong());
        verify(transport).observe(argThat(input -> input.fields().get("description").contains("PC")));
        assertTrue(read(ticket).path("finalAssessment").path("evidence").toString().contains("clarification:"));
    }

    @Test
    void eagerContextualEndpointCannotInferBeforeSeal() throws Exception {
        var ticket = create("Máy 15 phòng 1304 không lên nguồn");
        mvc.perform(post(path(ticket) + "/assistant/assessment-contextual").with(as("owner", "requester"))
                        .contentType("application/json").content("{\"contractVersion\":\"ticket-assessment-contextual@0.1.0\"}"))
                .andExpect(status().isConflict());
        mvc.perform(post(path(ticket) + "/assistant/classify").with(as("owner", "requester"))
                        .contentType("application/json").content("{\"contractVersion\":\"model-primary-assessment@0.1.0\"}"))
                .andExpect(status().isConflict());
        verifyNoInteractions(transport);
    }

    @Test
    void twoWorkersAdmitOneLogicalFinalAttempt() throws Exception {
        var ticket = create("Máy 15 phòng 1304 không lên nguồn");
        evaluate(ticket);
        var pool = Executors.newFixedThreadPool(2);
        try {
            var first = pool.submit(() -> worker.processOne());
            var second = pool.submit(() -> worker.processOne());
            assertEquals(List.of(false, true), List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS)).stream().sorted().toList());
        } finally { pool.shutdownNow(); }
        assertEquals(1L, jdbc.queryForObject("select count(*) from servicedesk_final_attempt where ticket_id=?", Long.class, id(ticket)));
        verify(transport, times(1)).observe(any());
    }

    @Test
    void answerDuringInferenceCannotPublishStaleOutput() throws Exception {
        var ticket = create("Máy 15 phòng 1304 không lên nguồn");
        evaluate(ticket);
        when(transport.observe(any())).thenAnswer(call -> {
            runAs("owner", "requester", () -> clarifications.append("d2-model", id(ticket),
                    new ClarificationRequest("ticket-clarification@0.1.0", UUID.randomUUID(), 0L,
                            List.of(new ClarificationRequest.Answer("location.room", "1305", true)))));
            return vote();
        });
        worker.processOne();
        var state = read(ticket);
        assertEquals("NEW", state.path("status").asText());
        assertTrue(state.path("finalAssessment").isNull());
        assertEquals("STALE", jdbc.queryForObject("select state from servicedesk_final_attempt where ticket_id=?", String.class, id(ticket)));
    }

    @Test
    void modelOutageIsTerminalManualWithoutRetryStorm() throws Exception {
        var ticket = create("Máy 15 phòng 1304 không lên nguồn");
        evaluate(ticket);
        when(transport.observe(any())).thenThrow(IntelligenceFailure.unavailable());
        worker.processOne();
        assertEquals("MANUAL_REQUIRED", read(ticket).path("status").asText());
        assertFalse(worker.processOne());
        verify(transport, times(1)).observe(any());
    }

    @Test
    void grantRevokedDuringInferenceIsNotPublished() throws Exception {
        var ticket = create("Máy 15 phòng 1304 không lên nguồn");
        evaluate(ticket);
        when(transport.observe(any())).thenAnswer(call -> {
            when(modelRights.verifyAssessment(anyString(), anyString())).thenThrow(CapabilityException.forbidden("Synthetic revocation"));
            return vote();
        });
        worker.processOne();
        assertEquals("MANUAL_REQUIRED", read(ticket).path("status").asText());
        assertTrue(read(ticket).path("finalAssessment").isNull());
    }

    @Test
    void changedGrantBeforeWorkerAdmissionMakesZeroModelCalls() throws Exception {
        var ticket = create("Máy 15 phòng 1304 không lên nguồn");
        evaluate(ticket);
        when(modelRights.verifyAssessment(anyString(), anyString())).thenReturn("different-model-grant");
        worker.processOne();
        assertEquals("MANUAL_REQUIRED", read(ticket).path("status").asText());
        verifyNoInteractions(transport);
    }

    @Test
    void roomOnlyRevisionReusesMaterialButRebindsFinalSnapshot() throws Exception {
        var ticket = create("Máy 15 phòng 1304 không lên nguồn");
        evaluate(ticket);
        worker.processOne();
        append(ticket, 0, List.of(new ClarificationRequest.Answer("location.room", "1305", true)));
        evaluate(ticket);
        worker.processOne();
        var result = read(ticket).path("finalAssessment");
        assertEquals(1, result.path("contextRevision").asLong());
        assertEquals("MODEL_REUSED_MATERIAL", result.path("assessmentStatus").asText());
        verify(transport, times(1)).observe(any());
    }

    @Test
    void persistedResultIsRedactedWhenReadRightsAreRevoked() throws Exception {
        var ticket = create("Máy 15 phòng 1304 không lên nguồn");
        evaluate(ticket);
        worker.processOne();
        when(rights.verifyContextual(anyString(), anyString(), anyString(), anyString()))
                .thenThrow(CapabilityException.forbidden("Synthetic revocation"));
        assertTrue(read(ticket).path("finalAssessment").isNull());
        verify(transport, times(1)).observe(any());
    }

    @Test
    void criticalSafetySkipsDuplicateAndModelWait() throws Exception {
        var ticket = create("Thiết bị rò điện trong phòng");
        assertEquals("MANUAL_REQUIRED", evaluate(ticket).path("status").asText());
        assertFalse(worker.processOne());
        verifyNoInteractions(transport);
    }

    @Test
    void humanReviewRecordsImmutableComparisonAndLateAnswerDoesNotReclassify() throws Exception {
        var ticket = create("Máy 15 phòng 1304 không lên nguồn");
        evaluate(ticket);
        worker.processOne();
        var state = read(ticket);
        mvc.perform(post(path(ticket) + "/review").with(as("coordinator", "coordinator")).contentType("application/json")
                        .content("{\"category\":\"SOFTWARE\",\"priority\":\"MEDIUM\",\"assignee\":null,\"reason\":\"Human correction\","
                                + "\"expectedContextRevision\":0,\"expectedIntakeVersion\":" + state.path("version").asLong() + "}"))
                .andExpect(status().isOk());
        append(ticket, 0, List.of(new ClarificationRequest.Answer("location.room", "1305", true)));
        assertEquals("REVIEWED", evaluate(ticket).path("status").asText());
        assertFalse(worker.processOne());
        assertEquals(1L, jdbc.queryForObject("select count(*) from servicedesk_ai_feedback where ticket_id=? "
                + "and human_decision->>'category'='SOFTWARE' and evaluation_status='NOT_REVIEWED_GOLD'", Long.class, id(ticket)));
        verify(transport, times(1)).observe(any());
    }

    private JsonNode create(String title) throws Exception {
        return body(mvc.perform(post(BASE).with(as("owner", "requester")).contentType("application/json")
                .content(json.writeValueAsString(Map.of("clientRequestId", UUID.randomUUID(), "title", title, "description", title))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
    }
    private JsonNode evaluate(JsonNode ticket) throws Exception {
        return body(mvc.perform(post(path(ticket) + "/intake/evaluate").with(as("owner", "requester")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
    }
    private JsonNode read(JsonNode ticket) throws Exception {
        return body(mvc.perform(get(path(ticket) + "/intake").with(as("owner", "requester")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
    }
    private void append(JsonNode ticket, long revision, List<ClarificationRequest.Answer> answers) throws Exception {
        mvc.perform(post(path(ticket) + "/clarifications").with(as("owner", "requester")).contentType("application/json")
                .content(json.writeValueAsString(new ClarificationRequest("ticket-clarification@0.1.0", UUID.randomUUID(), revision, answers))))
                .andExpect(status().isCreated());
    }
    private JsonNode body(String value) throws Exception { return json.readTree(value); }
    private static UUID id(JsonNode ticket) { return UUID.fromString(ticket.path("id").asText()); }
    private static String path(JsonNode ticket) { return BASE + "/" + id(ticket); }
    private static ClassificationObservation vote() {
        return new ClassificationObservation("PROPOSE", "HARDWARE", "a".repeat(64), "b".repeat(64), "UNVERIFIED_BY_MODEL", "NOT_AVAILABLE", false);
    }
    private static RequestPostProcessor as(String subject, String role) {
        return jwt().jwt(token -> token.subject(subject).claim("groups", List.of("/workspaces/d2-model/" + role)));
    }
    private static void runAs(String subject, String role, Runnable operation) {
        var previous = SecurityContextHolder.getContext();
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new JwtAuthenticationToken(Jwt.withTokenValue("synthetic-only").header("alg", "none")
                .subject(subject).claim("groups", List.of("/workspaces/d2-model/" + role)).build()));
        SecurityContextHolder.setContext(context);
        try { operation.run(); } finally { SecurityContextHolder.setContext(previous); }
    }
}
