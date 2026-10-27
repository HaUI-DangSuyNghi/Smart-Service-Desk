package vn.haui.dx.apps.servicedesk.intelligence.enrichment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import vn.haui.dx.apps.servicedesk.intelligence.context.ContextProcessingGate;
import vn.haui.dx.hpdi.common.api.CapabilityException;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"serviceDesk.context.enabled=true", "serviceDesk.context.mode=ASSISTED",
        "serviceDesk.enrichment.enabled=true", "serviceDesk.enrichment.timers-enabled=false"})
@AutoConfigureMockMvc
@org.springframework.context.annotation.Import(vn.haui.dx.core.workflow.internal.WorkflowStateFixture.class)
@EnabledIfEnvironmentVariable(named = "FILE_TEST_JDBC_URL", matches = ".+/file_storage")
class EnrichmentLifecycleIntegrationTest {
    private static final String BASE = "/api/apps/servicedesk/v1/workspaces/lifecycle-a";
    private final AtomicReference<Instant> now = new AtomicReference<>();

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", () -> System.getenv("FILE_TEST_JDBC_URL"));
        properties.add("spring.datasource.username", () -> "workflow");
        properties.add("spring.datasource.password", () -> System.getenv("FILE_TEST_DB_PASSWORD"));
        properties.add("dx.storage.root", () -> Path.of(System.getProperty("java.io.tmpdir"), "lifecycle-" + UUID.randomUUID()).toString());
        properties.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri", () -> "http://127.0.0.1:1/jwks");
    }

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private vn.haui.dx.core.workflow.internal.WorkflowStateFixture workflow;
    @SpyBean private EnrichmentCaseStore store;
    @MockBean private ContextProcessingGate processing;
    @MockBean private Clock clock;

    @BeforeEach
    void configure() {
        now.set(Instant.now());
        when(clock.instant()).thenAnswer(call -> now.get());
        when(processing.verifyReadiness(eq("lifecycle-a"), anyString(), anyString())).thenReturn("synthetic-lifecycle-grant");
    }

    @Test
    void completeTicketIsReadyWithoutWaitOrWorkflowMutation() throws Exception {
        var ticket = create("Máy 15 phòng 1304 không lên nguồn");
        assertEquals("AWAITING_REVIEW", ticket.path("state").asText());
        assertEquals("service-desk-reviewed", ticket.path("process").path("processKey").asText());
        assertEquals("PENDING", jdbc.queryForObject(
                "select status from servicedesk_assessment where ticket_id=?", String.class, id(ticket)));
        assertEquals(1L, jdbc.queryForObject(
                "select count(*) from servicedesk_assessment_history where ticket_id=? and event='QUEUED'",
                Long.class, id(ticket)));
        long[] before = Arrays.copyOf(workflow.counts(), 3);
        var state = evaluate(ticket);
        assertEquals("READY", state.path("status").asText());
        assertTrue(state.path("deadlineAt").isNull());
        assertFalse(state.path("workflowGateApplied").asBoolean());
        assertArrayEquals(before, Arrays.copyOf(workflow.counts(), 3));
        assertEquals(1, count(ticket, "EVALUATE_CONTEXT"));
    }

    @Test
    void failedEnrichmentEnrollmentRollsBackTicketProcessAndBackgroundJob() throws Exception {
        long[] before = workflow.counts();
        long assessments = jdbc.queryForObject("select count(*) from servicedesk_assessment", Long.class);
        long assessmentHistory = jdbc.queryForObject("select count(*) from servicedesk_assessment_history", Long.class);
        String title = "Synthetic enrollment rollback " + UUID.randomUUID();
        EnrichmentCaseStore target = org.springframework.test.util.AopTestUtils.getUltimateTargetObject(store);
        doThrow(CapabilityException.validation("Synthetic enrollment failure"))
                .when(target).enqueue(eq("lifecycle-a"), any(UUID.class), eq(0L));

        mvc.perform(post(BASE + "/tickets").with(as("owner", "lifecycle-a", "requester"))
                        .contentType("application/json").content(json.writeValueAsString(Map.of(
                                "clientRequestId", UUID.randomUUID(), "title", title, "description", title))))
                .andExpect(status().isBadRequest());

        assertArrayEquals(before, workflow.counts());
        assertEquals(0L, jdbc.queryForObject("select count(*) from servicedesk_ticket where title=?", Long.class, title));
        assertEquals(assessments, jdbc.queryForObject("select count(*) from servicedesk_assessment", Long.class));
        assertEquals(assessmentHistory, jdbc.queryForObject("select count(*) from servicedesk_assessment_history", Long.class));
    }

    @Test
    void optionalMissingDeviceAsksInParallelWithPendingDelivery() throws Exception {
        var ticket = create("Máy tính phòng 1304 không lên nguồn");
        var state = evaluate(ticket);
        assertEquals("OPTIONAL_ENRICHMENT", state.path("status").asText());
        assertEquals("PENDING", state.path("deliveryStatus").asText());
        assertFalse(state.path("workflowGateApplied").asBoolean());
        String request = state.path("requestInstanceId").asText();
        var replay = evaluate(ticket);
        assertEquals(request, replay.path("requestInstanceId").asText());
        assertEquals(1, count(ticket, "QUESTION_REQUESTED"));
    }

    @Test
    void safetyBypassesWaitAndAppearsInCoordinatorWorklist() throws Exception {
        var ticket = create("Thiết bị rò điện trong phòng");
        var state = evaluate(ticket);
        assertEquals("MANUAL_REVIEW", state.path("status").asText());
        assertTrue(state.path("manualReviewRequired").asBoolean());
        assertTrue(state.path("questions").isEmpty());
        assertTrue(state.path("deadlineAt").isNull());
        var list = mvc.perform(get(BASE + "/enrichment-pending").with(as("reviewer", "lifecycle-a", "coordinator")))
                .andExpect(status().isOk()).andReturn();
        assertTrue(list.getResponse().getContentAsString().contains(ticket.path("id").asText()));
    }

    @Test
    void coordinatorCanReadBeyondFirstFiftyCasesWithoutDuplicates() throws Exception {
        var created = new java.util.HashSet<String>();
        for (int index = 0; index < 51; index++) {
            created.add(create("Synthetic pagination " + index).path("id").asText());
        }
        var observed = new java.util.HashSet<String>();
        int page = 0;
        JsonNode items;
        do {
            items = body(mvc.perform(get(BASE + "/enrichment-pending").param("page", Integer.toString(page++))
                            .with(as("reviewer", "lifecycle-a", "coordinator")))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
            assertTrue(items.size() <= 50);
            for (var item : items) {
                assertTrue(observed.add(item.path("ticketId").asText()), "Repeated case across pages");
                assertFalse(item.path("workflowGateApplied").asBoolean());
            }
        } while (items.size() == 50 && page < 10);
        assertTrue(observed.containsAll(created), "Cases beyond the first page must remain accessible");
        mvc.perform(get(BASE + "/enrichment-pending").param("page", "-1")
                        .with(as("reviewer", "lifecycle-a", "coordinator"))).andExpect(status().isBadRequest());
        mvc.perform(get(BASE + "/enrichment-pending").param("page", "10001")
                        .with(as("reviewer", "lifecycle-a", "coordinator"))).andExpect(status().isBadRequest());
        mvc.perform(get(BASE + "/enrichment-pending").param("page", "1")
                        .with(as("owner", "lifecycle-a", "requester")))
                .andExpect(status().isForbidden());
    }

    @Test
    void reminderAndTimeoutAreDurableAndIdempotentAcrossWorkers() throws Exception {
        var ticket = create("Xin hỗ trợ, chưa rõ lỗi");
        evaluate(ticket);
        now.set(now.get().plusSeconds(3700));
        raceTimers();
        assertEquals(1, count(ticket, "REMINDER_DUE"));
        now.set(now.get().plusSeconds(86400));
        raceTimers();
        var state = getState(ticket, "owner");
        assertEquals("TIMED_OUT", state.path("status").asText());
        assertTrue(state.path("manualReviewRequired").asBoolean());
        assertEquals(1, count(ticket, "MANUAL_REVIEW_REQUIRED"));
        assertEquals(1L, jdbc.queryForObject("select count(*) from servicedesk_ticket where id=?", Long.class, id(ticket)));
        assertEquals(1L, jdbc.queryForObject("select count(*) from servicedesk_enrichment_transition "
                + "where ticket_id=? and reason_code='CLARIFICATION_TIMEOUT'", Long.class, id(ticket)));
    }

    @Test
    void lateAnswerIsSavedButCannotUndoManualDecision() throws Exception {
        var ticket = create("Xin hỗ trợ");
        evaluate(ticket);
        now.set(now.get().plusSeconds(90000));
        store.expireDue();
        append(ticket, 0, "device.type", "PC");
        var state = evaluate(ticket);
        assertEquals(1, state.path("contextRevision").asLong());
        assertTrue(state.path("manualReviewRequired").asBoolean());
        assertTrue(state.path("reasonCodes").toString().contains("LATE_ANSWER_REVIEW_REQUIRED"));
        assertEquals("MANUAL_REVIEW", state.path("status").asText());
    }

    @Test
    void rapidRevisionsSupersedeOldJobsAndDoNotRepeatOldQuestions() throws Exception {
        var ticket = create("Xin hỗ trợ");
        append(ticket, 0, "location.room", "1304");
        append(ticket, 1, "device.type", "PC");
        var state = evaluate(ticket);
        assertEquals(2, state.path("contextRevision").asLong());
        assertEquals(2L, jdbc.queryForObject("select count(*) from servicedesk_enrichment_outbox where ticket_id=? "
                + "and event_type='EVALUATE_CONTEXT' and state='SUPERSEDED'", Long.class, id(ticket)));
        assertEquals(1L, jdbc.queryForObject("select sum(attempts) from servicedesk_enrichment_outbox "
                + "where ticket_id=? and event_type='EVALUATE_CONTEXT'", Long.class, id(ticket)));
    }

    @Test
    void outboxFailureRollsBackAnswerRevisionAndAudit() throws Exception {
        var ticket = create("Xin hỗ trợ");
        EnrichmentCaseStore target = org.springframework.test.util.AopTestUtils.getUltimateTargetObject(store);
        doThrow(CapabilityException.validation("Synthetic outbox failure"))
                .when(target).enqueue(eq("lifecycle-a"), eq(id(ticket)), eq(1L));
        mvc.perform(post(path(ticket) + "/clarifications").with(as("owner", "lifecycle-a", "requester"))
                        .contentType("application/json").content(answer(0, "device.type", "PC")))
                .andExpect(status().isBadRequest());
        assertEquals(0L, jdbc.queryForObject("select context_revision from servicedesk_ticket where id=?", Long.class, id(ticket)));
        assertEquals(0L, jdbc.queryForObject("select count(*) from servicedesk_ticket_clarification where ticket_id=?", Long.class, id(ticket)));
        assertEquals(1, count(ticket, "EVALUATE_CONTEXT"));
    }

    @Test
    void aclAndExpiredGrantNeverLeakDerivedQuestions() throws Exception {
        var ticket = create("Xin hỗ trợ");
        evaluate(ticket);
        mvc.perform(get(path(ticket) + "/enrichment-state").with(as("other", "lifecycle-a", "requester")))
                .andExpect(status().isNotFound());
        mvc.perform(get(path(ticket) + "/enrichment-state").with(as("foreign", "lifecycle-b", "coordinator")))
                .andExpect(status().isForbidden());
        mvc.perform(get(BASE + "/enrichment-pending").with(as("owner", "lifecycle-a", "requester")))
                .andExpect(status().isForbidden());
        when(processing.verifyReadiness(anyString(), anyString(), anyString()))
                .thenThrow(CapabilityException.forbidden("Expired"));
        var state = getState(ticket, "owner");
        assertTrue(state.path("questions").isEmpty());
        assertTrue(state.path("readiness").isNull());
        assertTrue(state.path("manualReviewRequired").asBoolean());
    }

    @Test
    void abandonedLeaseCanRecoverOnceThenBecomesManual() throws Exception {
        var ticket = create("Xin hỗ trợ");
        now.set(now.get().plusSeconds(1));
        assertTrue(store.claim("lifecycle-a", id(ticket)).isPresent());
        assertTrue(store.claim("lifecycle-a", id(ticket)).isEmpty());
        now.set(now.get().plusSeconds(61));
        assertTrue(store.claim("lifecycle-a", id(ticket)).isPresent());
        now.set(now.get().plusSeconds(61));
        assertTrue(store.claim("lifecycle-a", id(ticket)).isEmpty());
        assertEquals("MANUAL_REVIEW", getState(ticket, "owner").path("status").asText());
        assertEquals(2L, jdbc.queryForObject("select sum(attempts) from servicedesk_enrichment_outbox "
                + "where ticket_id=? and event_type='EVALUATE_CONTEXT'", Long.class, id(ticket)));
    }

    @Test
    void unresolvedAnswersCannotCreateThirdQuestionRound() throws Exception {
        var ticket = create("Xin hỗ trợ");
        evaluate(ticket);
        append(ticket, 0, "location.room", "1304");
        evaluate(ticket);
        append(ticket, 1, "location.floor", "3");
        var state = evaluate(ticket);
        assertEquals("MANUAL_REVIEW", state.path("status").asText());
        assertEquals(2, state.path("roundCount").asInt());
        assertEquals(2L, jdbc.queryForObject("select count(*) from servicedesk_enrichment_question_request where ticket_id=?",
                Long.class, id(ticket)));
    }

    @Test
    void timeoutAndAnswerRaceKeepsAnswerAndManualVisibility() throws Exception {
        var ticket = create("Xin hỗ trợ");
        evaluate(ticket);
        now.set(now.get().plusSeconds(90000));
        var pool = Executors.newFixedThreadPool(2);
        var start = new CountDownLatch(1);
        try {
            var timer = pool.submit(() -> { start.await(); return store.expireDue(); });
            var answer = pool.submit(() -> { start.await(); append(ticket, 0, "device.type", "PC"); return true; });
            start.countDown();
            timer.get(20, TimeUnit.SECONDS);
            assertTrue(answer.get(20, TimeUnit.SECONDS));
        } finally {
            pool.shutdownNow();
        }
        var state = getState(ticket, "owner");
        assertEquals(1, state.path("contextRevision").asLong());
        assertTrue(state.path("manualReviewRequired").asBoolean());
        assertNotEquals("READY", state.path("status").asText());
    }

    @Test
    void expiredWaitIsStillLateBeforeAnyTimerHasPolled() throws Exception {
        var ticket = create("Xin hỗ trợ");
        evaluate(ticket);
        now.set(now.get().plusSeconds(90000));
        append(ticket, 0, "device.type", "PC");
        var state = evaluate(ticket);
        assertEquals("MANUAL_REVIEW", state.path("status").asText());
        assertTrue(state.path("reasonCodes").toString().contains("LATE_ANSWER_REVIEW_REQUIRED"));
    }

    private void raceTimers() throws Exception {
        var pool = Executors.newFixedThreadPool(2);
        var start = new CountDownLatch(1);
        try {
            var jobs = List.of(pool.submit(() -> { start.await(); return store.expireDue(); }),
                    pool.submit(() -> { start.await(); return store.expireDue(); }));
            start.countDown();
            for (var job : jobs) { job.get(20, TimeUnit.SECONDS); }
        } finally {
            pool.shutdownNow();
        }
    }

    private JsonNode create(String text) throws Exception {
        return body(mvc.perform(post(BASE + "/tickets").with(as("owner", "lifecycle-a", "requester"))
                        .contentType("application/json").content(json.writeValueAsString(Map.of(
                                "clientRequestId", UUID.randomUUID(), "title", text, "description", text))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
    }

    private JsonNode evaluate(JsonNode ticket) throws Exception {
        now.set(now.get().plusSeconds(1));
        return body(mvc.perform(post(path(ticket) + "/enrichment-state/evaluate")
                        .with(as("owner", "lifecycle-a", "requester")).contentType("application/json")
                        .content("{\"contractVersion\":\"ticket-enrichment-lifecycle@0.1.0\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
    }

    private JsonNode getState(JsonNode ticket, String owner) throws Exception {
        return body(mvc.perform(get(path(ticket) + "/enrichment-state").with(as(owner, "lifecycle-a", "requester")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
    }

    private void append(JsonNode ticket, long revision, String slot, String value) throws Exception {
        mvc.perform(post(path(ticket) + "/clarifications").with(as("owner", "lifecycle-a", "requester"))
                        .contentType("application/json").content(answer(revision, slot, value)))
                .andExpect(status().isCreated());
    }

    private String answer(long revision, String slot, String value) throws Exception {
        return json.writeValueAsString(Map.of("contractVersion", "ticket-clarification@0.1.0", "clientAnswerId", UUID.randomUUID(),
                "expectedContextRevision", revision, "answers", List.of(Map.of("slot", slot, "value", value, "correction", false))));
    }

    private int count(JsonNode ticket, String type) {
        return jdbc.queryForObject("select count(*) from servicedesk_enrichment_outbox where ticket_id=? and event_type=?",
                Integer.class, id(ticket), type);
    }

    private JsonNode body(String text) throws Exception {
        var value = json.readTree(text);
        if ("ticket-enrichment-lifecycle@0.1.0".equals(value.path("contractVersion").asText())) {
            vn.haui.dx.apps.servicedesk.intelligence.context.ContextSchemaAssertions.verify(
                    json, "ticket-enrichment-lifecycle-v0.1.schema.json", value);
        }
        return value;
    }
    private UUID id(JsonNode ticket) { return UUID.fromString(ticket.path("id").asText()); }
    private String path(JsonNode ticket) { return BASE + "/tickets/" + id(ticket); }

    private static RequestPostProcessor as(String subject, String workspace, String... roles) {
        return jwt().jwt(token -> token.subject(subject).claim("groups", Arrays.stream(roles)
                .map(role -> "/workspaces/" + workspace + "/" + role).toList()));
    }
}
