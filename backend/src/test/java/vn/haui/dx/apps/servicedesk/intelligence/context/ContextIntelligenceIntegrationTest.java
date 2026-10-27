package vn.haui.dx.apps.servicedesk.intelligence.context;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;

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

import vn.haui.dx.apps.servicedesk.intelligence.duplicate.DuplicateDecisionEngine;
import vn.haui.dx.hpdi.common.api.CapabilityException;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@SpringBootTest(properties = {"serviceDesk.context.enabled=true", "serviceDesk.context.mode=ASSISTED"})
@AutoConfigureMockMvc
@org.springframework.context.annotation.Import(vn.haui.dx.core.workflow.internal.WorkflowStateFixture.class)
@EnabledIfEnvironmentVariable(named = "FILE_TEST_JDBC_URL", matches = ".+/file_storage")
class ContextIntelligenceIntegrationTest {
    private static final String BASE = "/api/apps/servicedesk/v1/workspaces/context-a/tickets";
    private static final String TEXT = "Máy 15 phòng 1304 không lên nguồn";

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", () -> System.getenv("FILE_TEST_JDBC_URL"));
        properties.add("spring.datasource.username", () -> "workflow");
        properties.add("spring.datasource.password", () -> System.getenv("FILE_TEST_DB_PASSWORD"));
        properties.add("dx.storage.root", () -> Path.of(System.getProperty("java.io.tmpdir"), "context-" + UUID.randomUUID()).toString());
        properties.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri", () -> "http://127.0.0.1:1/jwks");
    }

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private vn.haui.dx.apps.servicedesk.clarification.TicketClarificationService clarifications;
    @Autowired private vn.haui.dx.core.workflow.internal.WorkflowStateFixture workflow;
    @MockBean private ContextProcessingGate processing;
    @SpyBean private ContextSlotExtractor extractor;
    @SpyBean private DuplicateDecisionEngine decisions;
    @SpyBean private vn.haui.dx.hpdi.human.api.ActivityJournal audit;

    @BeforeEach
    void grant() {
        when(processing.verify(eq("context-a"), anyString())).thenReturn("synthetic-test-grant");
    }

    @Test
    void appendIsIdempotentAndDoesNotCreateTicketOrWorkflow() throws Exception {
        var ticket = create("owner", TEXT);
        long[] before = counts();
        UUID answerId = UUID.randomUUID();
        String payload = answer(answerId, 0, "location.room", "1305", true);
        var first = append(ticket, "owner", payload, 201);
        schema("ticket-clarification-result-v0.1.schema.json", first);
        assertEquals(1, first.path("contextRevision").asLong());
        var replay = append(ticket, "owner", payload, 200);
        assertEquals(first.path("answerId"), replay.path("answerId"));
        assertTrue(replay.path("replayed").asBoolean());
        assertArrayEquals(before, counts());
        assertEquals(1L, jdbc.queryForObject(
                "select count(*) from servicedesk_ticket_clarification where ticket_id=?", Long.class, id(ticket)));
        assertEquals(1L, jdbc.queryForObject(
                "select count(*) from audit_event where action=? and resource=?",
                Long.class, "servicedesk.ticket.clarify", id(ticket).toString()));
        assertEquals(TEXT, jdbc.queryForObject("select title from servicedesk_ticket where id=?", String.class, id(ticket)));

        var enrichment = advisory(ticket, "owner", "requester", "enrichment", 200);
        schema("ticket-enrichment-v0.1.schema.json", enrichment);
        assertEquals("1305", enrichment.path("context").path("location.room").path("value").asText());
        assertEquals(3, enrichment.path("context").path("location.room").path("evidence").size());
        assertEquals("AI07_REFRESH_REQUIRED", enrichment.path("ai07Status").asText());
    }

    @Test
    void staleRevisionAndChangedReplayNeverWrite() throws Exception {
        var ticket = create("owner", TEXT);
        UUID key = UUID.randomUUID();
        append(ticket, "owner", answer(key, 0, "device.identifier", "16", true), 201);
        append(ticket, "owner", answer(key, 0, "device.identifier", "17", true), 409);
        append(ticket, "owner", answer(UUID.randomUUID(), 0, "device.identifier", "17", true), 409);
        assertEquals(1L, jdbc.queryForObject(
                "select count(*) from servicedesk_ticket_clarification where ticket_id=?", Long.class, id(ticket)));
        append(ticket, "owner", answer(UUID.randomUUID(), 1, "workBlocked", "có", false), 201);
        var oldReplay = append(ticket, "owner", answer(key, 0, "device.identifier", "16", true), 200);
        assertEquals(1, oldReplay.path("contextRevision").asLong());
        mvc.perform(get(BASE + "/" + id(ticket) + "/context-revision").with(as("owner", "context-a", "requester")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.contextRevision").value(2));
    }

    @Test
    void concurrentAppendsUseAtomicRevisionAndConcurrentReplayWritesOnce() throws Exception {
        var ticket = create("owner", TEXT);
        var keys = List.of(UUID.randomUUID(), UUID.randomUUID());
        assertEquals(List.of(201, 409), race(ticket, keys, 0));
        var next = create("owner", TEXT);
        UUID sameKey = UUID.randomUUID();
        assertEquals(List.of(200, 201), race(next, List.of(sameKey, sameKey), 0));
        assertEquals(1L, jdbc.queryForObject(
                "select count(*) from servicedesk_ticket_clarification where ticket_id=?", Long.class, id(next)));
    }

    @Test
    void persistenceDoesNotDependOnProcessingGrantOrAiAvailability() throws Exception {
        var ticket = create("owner", TEXT);
        when(processing.verify(anyString(), anyString())).thenThrow(CapabilityException.forbidden("Unavailable"));
        append(ticket, "owner", answer(UUID.randomUUID(), 0, "workBlocked", "có", false), 201);
        verifyNoInteractions(processing);
        advisory(ticket, "owner", "requester", "enrichment", 403);
    }

    @Test
    void crossOwnerCrossWorkspaceAndRealmAdminCannotAccessTicket() throws Exception {
        var ticket = create("owner", TEXT);
        String payload = answer(UUID.randomUUID(), 0, "location.room", "1305", false);
        append(ticket, "other", payload, 404);
        mvc.perform(post(BASE + "/" + id(ticket) + "/clarifications")
                        .with(as("foreign", "context-b", "requester")).contentType("application/json").content(payload))
                .andExpect(status().isForbidden());
        mvc.perform(post(BASE + "/" + id(ticket) + "/clarifications")
                        .with(jwt().jwt(token -> token.subject("admin").claim("realm_access", Map.of("roles", List.of("platform-admin")))))
                        .contentType("application/json").content(payload))
                .andExpect(status().isForbidden());
        advisory(ticket, "other", "requester", "enrichment", 404);
        verifyNoInteractions(processing);
    }

    @Test
    void requesterCannotSeeOtherOwnersCandidateIdentity() throws Exception {
        var hidden = create("hidden-owner", TEXT);
        var query = create("owner", TEXT);
        var requester = advisory(query, "owner", "requester", "duplicates", 200);
        schema("ticket-duplicate-probe-v0.1.schema.json", requester);
        assertTrue(requester.path("candidates").isEmpty());
        assertFalse(requester.toString().contains(id(hidden).toString()));
        var reviewer = advisory(query, "reviewer", "coordinator", "duplicates", 200);
        schema("ticket-duplicate-probe-v0.1.schema.json", reviewer);
        assertFalse(reviewer.path("candidates").isEmpty());
        assertEquals("POSSIBLE_DUPLICATE", reviewer.path("decision").asText());
        assertTrue(reviewer.path("duplicateOf").isNull());
        assertFalse(reviewer.path("disposition").path("autoMergeAllowed").asBoolean());
        assertTrue(reviewer.toString().contains("VERIFIED_CATEGORY"));
    }

    @Test
    void closedOrSuspendedProcessIsNotCandidate() throws Exception {
        // Workspace riêng cho ca này để không phụ thuộc ticket của test khác.
        var candidate = create("closed-owner", TEXT);
        workflow.cancel(candidate.path("process").path("processInstanceId").asText());
        var query = create("closed-owner", TEXT);
        var result = advisory(query, "closed-owner", "requester", "duplicates", 200);
        assertTrue(result.path("candidates").isEmpty());
        assertEquals("INSUFFICIENT_EVIDENCE", result.path("decision").asText());
    }

    @Test
    void candidateClosingDuringRankingInvalidatesResponse() throws Exception {
        var candidate = create("race-owner", TEXT);
        var query = create("race-owner", TEXT);
        doAnswer(call -> {
            var result = call.callRealMethod();
            workflow.cancel(candidate.path("process").path("processInstanceId").asText());
            return result;
        }).when(decisions).decide(any(), any());
        advisory(query, "race-owner", "requester", "duplicates", 409);
    }

    @Test
    void newClarificationDuringExtractionInvalidatesResponse() throws Exception {
        var ticket = create("drift-owner", TEXT);
        doAnswer(call -> {
            var result = call.callRealMethod();
            clarifications.append("context-a", id(ticket), json.readValue(
                    answer(UUID.randomUUID(), 0, "location.room", "1305", true),
                    vn.haui.dx.apps.servicedesk.clarification.ClarificationRequest.class));
            return result;
        }).when(extractor).extract(argThat(snapshot -> snapshot.ticketId().equals(id(ticket))));
        advisory(ticket, "drift-owner", "requester", "enrichment", 409);
    }

    @Test
    void revokedGrantFailsClosedBeforePublishing() throws Exception {
        var ticket = create("owner", TEXT);
        when(processing.verify(eq("context-a"), anyString()))
                .thenReturn("grant-before").thenThrow(CapabilityException.forbidden("Revoked"));
        advisory(ticket, "owner", "requester", "enrichment", 403);
    }

    @Test
    void rejectsAuthorityFieldsUnknownSlotsCoercionAndOversizedBodies() throws Exception {
        var ticket = create("owner", TEXT);
        var invalid = List.of(
                answer(UUID.randomUUID(), 0, "priority", "HIGH", false),
                answer(UUID.randomUUID(), 0, "location.room", "x".repeat(257), false),
                answer(UUID.randomUUID(), 0, "location.room", "1304", false).replace("\"expectedContextRevision\":0", "\"expectedContextRevision\":0.5"),
                answer(UUID.randomUUID(), 0, "location.room", "1304", false).replace("\"correction\":false", "\"correction\":\"true\""),
                "{\"contractVersion\":\"ticket-clarification@0.1.0\",\"actor\":\"admin\"}",
                "{" + " ".repeat(16384) + "}");
        for (var payload : invalid) {
            append(ticket, "owner", payload, 400);
        }
    }

    @Test
    void auditFailureRollsBackRevisionAndAnswerTogether() throws Exception {
        var ticket = create("owner", TEXT);
        vn.haui.dx.hpdi.human.api.ActivityJournal auditTarget =
                org.springframework.test.util.AopTestUtils.getUltimateTargetObject(audit);
        doThrow(CapabilityException.validation("Synthetic audit failure"))
                .when(auditTarget).record(eq("context-a"), argThat(event -> event.action().equals("servicedesk.ticket.clarify")));
        append(ticket, "owner", answer(UUID.randomUUID(), 0, "location.room", "1305", true), 400);
        assertEquals(0L, jdbc.queryForObject(
                "select context_revision from servicedesk_ticket where id=?", Long.class, id(ticket)));
        assertEquals(0L, jdbc.queryForObject(
                "select count(*) from servicedesk_ticket_clarification where ticket_id=?", Long.class, id(ticket)));
        mvc.perform(get(BASE + "/" + id(ticket) + "/context-revision").with(as("owner", "context-a", "requester")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.contextRevision").value(0));
    }

    @Test
    void candidateScanHasServerEnforcedLimit() throws Exception {
        for (int index = 0; index < 52; index++) {
            create("bounded-owner", TEXT);
        }
        var query = create("bounded-owner", TEXT);
        var result = advisory(query, "bounded-owner", "requester", "duplicates", 200);
        assertEquals(50, result.path("scanned").asInt());
        assertTrue(result.path("candidateWindowFull").asBoolean());
        assertTrue(result.path("candidates").isEmpty());
    }

    @Test
    void advisoryRequestCannotSupplyCandidateOrOperationalAuthority() throws Exception {
        var ticket = create("owner", TEXT);
        for (String extra : List.of("\"candidateRef\":\"" + UUID.randomUUID() + "\"",
                "\"operationalAuthority\":true", "\"contextRevision\":999")) {
            mvc.perform(post(BASE + "/" + id(ticket) + "/assistant/duplicates")
                            .with(as("owner", "context-a", "requester")).contentType("application/json")
                            .content("{\"contractVersion\":\"ticket-duplicate-probe@0.1.0\"," + extra + "}"))
                    .andExpect(status().isBadRequest());
        }
        verifyNoInteractions(processing, extractor);
    }

    private List<Integer> race(JsonNode ticket, List<UUID> keys, long revision) throws Exception {
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(2);
        try {
            var jobs = keys.stream().map(key -> pool.submit(() -> {
                ready.countDown();
                assertTrue(start.await(10, TimeUnit.SECONDS));
                return mvc.perform(post(BASE + "/" + id(ticket) + "/clarifications")
                                .with(as("owner", "context-a", "requester"))
                                .contentType("application/json").content(answer(key, revision, "device.identifier", "16", true)))
                        .andReturn().getResponse().getStatus();
            })).toList();
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();
            var values = new java.util.ArrayList<Integer>();
            for (var job : jobs) {
                values.add(job.get(30, TimeUnit.SECONDS));
            }
            return values.stream().sorted().toList();
        } finally {
            pool.shutdownNow();
        }
    }

    private JsonNode create(String owner, String text) throws Exception {
        var result = mvc.perform(post(BASE).with(as(owner, "context-a", "requester"))
                        .contentType("application/json").content(json.writeValueAsString(Map.of(
                                "clientRequestId", UUID.randomUUID(), "title", text, "description", text))))
                .andExpect(status().isCreated()).andReturn();
        return json.readTree(result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
    }

    private JsonNode append(JsonNode ticket, String user, String payload, int expected) throws Exception {
        var result = mvc.perform(post(BASE + "/" + id(ticket) + "/clarifications")
                        .with(as(user, "context-a", "requester")).contentType("application/json").content(payload))
                .andExpect(status().is(expected)).andReturn();
        return json.readTree(result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
    }

    private JsonNode advisory(JsonNode ticket, String user, String role, String operation, int expected) throws Exception {
        String version = operation.equals("enrichment") ? ContextIntelligenceService.ENRICHMENT_VERSION
                : ContextIntelligenceService.DUPLICATE_VERSION;
        var result = mvc.perform(post(BASE + "/" + id(ticket) + "/assistant/" + operation)
                        .with(as(user, "context-a", role)).contentType("application/json")
                        .content(json.writeValueAsString(Map.of("contractVersion", version))))
                .andExpect(status().is(expected)).andReturn();
        return json.readTree(result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
    }

    private String answer(UUID key, long revision, String slot, String value, boolean correction) throws Exception {
        return json.writeValueAsString(Map.of("contractVersion", "ticket-clarification@0.1.0", "clientAnswerId", key,
                "expectedContextRevision", revision, "answers", List.of(Map.of("slot", slot, "value", value, "correction", correction))));
    }

    private void schema(String name, JsonNode value) throws Exception {
        try (var stream = getClass().getResourceAsStream("/servicedesk-contract/" + name)) {
            assertNotNull(stream);
            var schema = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012).getSchema(json.readTree(stream));
            assertTrue(schema.validate(value).isEmpty(), () -> schema.validate(value).toString());
        }
    }

    private long[] counts() {
        return new long[] {
            jdbc.queryForObject("select count(*) from servicedesk_ticket", Long.class),
            workflow.counts()[0], workflow.counts()[1], workflow.counts()[2]
        };
    }

    private static UUID id(JsonNode ticket) { return UUID.fromString(ticket.path("id").asText()); }

    private static RequestPostProcessor as(String subject, String workspace, String... roles) {
        return jwt().jwt(token -> token.subject(subject).claim("groups", Arrays.stream(roles)
                .map(role -> "/workspaces/" + workspace + "/" + role).toList()));
    }
}
