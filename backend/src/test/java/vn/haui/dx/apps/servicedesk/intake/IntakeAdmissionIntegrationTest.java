package vn.haui.dx.apps.servicedesk.intake;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import vn.haui.dx.hpdi.common.api.CapabilityException;
import vn.haui.dx.hpdi.human.api.ActivityJournal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"serviceDesk.intake.new-tickets=true", "service-desk.background.scheduled=false"})
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "FILE_TEST_JDBC_URL", matches = ".+/file_storage")
class IntakeAdmissionIntegrationTest {
    private static final String BASE = "/api/apps/servicedesk/v1/workspaces/lab-a/tickets";
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired IntakeAdmission admission;
    @SpyBean ActivityJournal audit;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> System.getenv("FILE_TEST_JDBC_URL"));
        registry.add("spring.datasource.username", () -> "workflow");
        registry.add("spring.datasource.password", () -> System.getenv("FILE_TEST_DB_PASSWORD"));
        registry.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri", () -> "http://127.0.0.1:1/jwks");
    }

    @Test
    void createPersistsAdmissionAndDoesNotClaimTrueWorkflowWait() throws Exception {
        var ticket = create();
        var state = state(ticket);
        assertEquals("NEW", state.path("status").asText());
        assertTrue(state.path("reviewTaskMayExist").asBoolean());
        assertFalse(state.path("operationalAuthority").asBoolean());
        assertEquals("AWAITING_REVIEW", ticket.path("state").asText());
        assertEquals(1L, jdbc.queryForObject("select count(*) from servicedesk_assessment where ticket_id=?", Long.class, id(ticket)));
    }

    @Test
    void prematureReviewCannotCreateReviewOrAgentWork() throws Exception {
        var ticket = create();
        review(ticket, 0, 0, 409);
        assertEquals(0L, jdbc.queryForObject("select count(*) from servicedesk_review where ticket_id=?", Long.class, id(ticket)));
        assertEquals("NEW", state(ticket).path("status").asText());
    }

    @Test
    void genericTaskCompleteCannotBypassAdmission() throws Exception {
        var ticket = create();
        String path = "/api/hpdi/v1/workspaces/lab-a/tasks/" + ticket.path("taskId").asText();
        mvc.perform(post(path + "/claim").with(as("coordinator", "coordinator"))).andExpect(status().isOk());
        mvc.perform(post(path + "/complete").with(as("coordinator", "coordinator"))
                        .contentType("application/json").content("{\"expectedTaskKey\":\"review\",\"outcome\":\"approved\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void manualReleaseIsAuditedIdempotentAndBoundToSnapshot() throws Exception {
        var ticket = create();
        String request = releaseBody(0, 0, UUID.randomUUID(), "Synthetic manual review");
        var first = release(ticket, request, 200);
        var replay = release(ticket, request, 200);
        assertEquals(first, replay);
        assertEquals("REVIEW_RELEASED", first.path("status").asText());
        assertEquals(0, first.path("sealedRevision").asLong());
        assertEquals(1L, jdbc.queryForObject("select count(*) from servicedesk_intake_receipt where ticket_id=?", Long.class, id(ticket)));
        assertEquals(1L, jdbc.queryForObject("select count(*) from audit_event where action='servicedesk.intake.release' and resource=?",
                Long.class, id(ticket).toString()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"requester", "agent", "platform-admin"})
    void unauthorizedRoleCannotRelease(String role) throws Exception {
        var ticket = create();
        mvc.perform(post(path(ticket) + "/intake/manual-release").with(as("owner", role))
                        .contentType("application/json").content(releaseBody(0, 0, UUID.randomUUID(), "Synthetic reason")))
                .andExpect(status().isForbidden());
    }

    @Test
    void creatorCoordinatorCannotReleaseOwnTicket() throws Exception {
        var ticket = create();
        mvc.perform(post(path(ticket) + "/intake/manual-release").with(as("owner", "requester", "coordinator"))
                        .contentType("application/json").content(releaseBody(0, 0, UUID.randomUUID(), "Synthetic reason")))
                .andExpect(status().isForbidden());
    }

    @Test
    void staleVersionAndReusedOperationWithDifferentReasonConflict() throws Exception {
        var ticket = create();
        release(ticket, releaseBody(1, 0, UUID.randomUUID(), "Synthetic stale"), 409);
        release(ticket, releaseBody(0, 2, UUID.randomUUID(), "Synthetic stale"), 409);
        UUID operation = UUID.randomUUID();
        release(ticket, releaseBody(0, 0, operation, "Synthetic first"), 200);
        release(ticket, releaseBody(0, 0, operation, "Synthetic changed"), 409);
    }

    @Test
    void clarificationInvalidatesReleasedSnapshotAndReviewGetsConflict() throws Exception {
        var ticket = create();
        release(ticket, releaseBody(0, 0, UUID.randomUUID(), "Synthetic release"), 200);
        append(ticket);
        assertEquals("NEW", state(ticket).path("status").asText());
        review(ticket, 0, 1, 409);
        assertEquals(1L, jdbc.queryForObject("select count(*) from servicedesk_intake_receipt where ticket_id=?", Long.class, id(ticket)));
    }

    @Test
    void timeoutEscalatesWithoutAutoApprovingAndRemainsManuallyReleasable() throws Exception {
        var ticket = create();
        jdbc.update("update servicedesk_intake_admission set deadline_at=clock_timestamp()-interval '1 second' where ticket_id=?", id(ticket));
        admission.expire();
        assertEquals("MANUAL_REQUIRED", state(ticket).path("status").asText());
        release(ticket, releaseBody(0, 1, UUID.randomUUID(), "Timeout reviewed manually"), 200);
    }

    @Test
    void releasedReviewRequiresExactCasAndLateAnswersDoNotReapprove() throws Exception {
        var ticket = create();
        release(ticket, releaseBody(0, 0, UUID.randomUUID(), "Synthetic manual release"), 200);
        review(ticket, 0, 0, 409);
        review(ticket, 0, 1, 200);
        append(ticket);
        var late = state(ticket);
        assertEquals("REVIEWED", late.path("status").asText());
        assertTrue(late.path("lateNotice").asBoolean());
        assertEquals(1L, jdbc.queryForObject("select count(*) from servicedesk_review where ticket_id=?", Long.class, id(ticket)));
    }

    @Test
    void releaseAuditFailureRollsBackReceiptAndState() throws Exception {
        var ticket = create();
        ActivityJournal target = org.springframework.test.util.AopTestUtils.getUltimateTargetObject(audit);
        doThrow(CapabilityException.validation("Synthetic audit failure")).when(target).record(eq("lab-a"),
                argThat(activity -> "servicedesk.intake.release".equals(activity.action())));
        release(ticket, releaseBody(0, 0, UUID.randomUUID(), "Synthetic reason"), 400);
        assertEquals("NEW", state(ticket).path("status").asText());
        assertEquals(0L, jdbc.queryForObject("select count(*) from servicedesk_intake_receipt where ticket_id=?", Long.class, id(ticket)));
    }

    @Test
    void requesterCannotReadOtherTicketOrWorkspace() throws Exception {
        var ticket = create();
        mvc.perform(get(path(ticket) + "/intake").with(as("other", "requester"))).andExpect(status().isNotFound());
        mvc.perform(get(path(ticket).replace("lab-a", "lab-b") + "/intake").with(as("owner", "requester")))
                .andExpect(status().isForbidden());
    }

    @Test
    void twoManualReleasesHaveOneWinner() throws Exception {
        var ticket = create();
        var pool = Executors.newFixedThreadPool(2);
        try {
            java.util.concurrent.Callable<Integer> action = () -> mvc.perform(post(path(ticket) + "/intake/manual-release")
                    .with(as("coordinator", "coordinator")).contentType("application/json")
                    .content(releaseBody(0, 0, UUID.randomUUID(), "Synthetic race")))
                    .andReturn().getResponse().getStatus();
            var first = pool.submit(action);
            var second = pool.submit(action);
            assertEquals(List.of(200, 409), List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS)).stream().sorted().toList());
        } finally { pool.shutdownNow(); }
    }

    private JsonNode create() throws Exception {
        var result = mvc.perform(post(BASE).with(as("owner", "requester")).contentType("application/json")
                        .content(json.writeValueAsString(Map.of("clientRequestId", UUID.randomUUID(), "title", "Synthetic intake",
                                "description", "Synthetic intake request"))))
                .andExpect(status().isCreated()).andReturn();
        return json.readTree(result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
    }

    private JsonNode state(JsonNode ticket) throws Exception {
        return json.readTree(mvc.perform(get(path(ticket) + "/intake").with(as("owner", "requester")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private String releaseBody(long revision, long version, UUID operation, String reason) throws Exception {
        return json.writeValueAsString(Map.of("expectedContextRevision", revision, "expectedVersion", version,
                "operationId", operation, "reason", reason));
    }

    private JsonNode release(JsonNode ticket, String body, int expected) throws Exception {
        return json.readTree(mvc.perform(post(path(ticket) + "/intake/manual-release").with(as("coordinator", "coordinator"))
                        .contentType("application/json").content(body)).andExpect(status().is(expected))
                .andReturn().getResponse().getContentAsString());
    }

    private void review(JsonNode ticket, long revision, long version, int expected) throws Exception {
        String body = "{\"category\":\"HARDWARE\",\"priority\":\"HIGH\",\"assignee\":null,\"reason\":\"Synthetic review\","
                + "\"expectedContextRevision\":" + revision + ",\"expectedIntakeVersion\":" + version + "}";
        mvc.perform(post(path(ticket) + "/review").with(as("coordinator", "coordinator"))
                .contentType("application/json").content(body)).andExpect(status().is(expected));
    }

    private void append(JsonNode ticket) throws Exception {
        mvc.perform(post(path(ticket) + "/clarifications").with(as("owner", "requester")).contentType("application/json")
                .content(json.writeValueAsString(Map.of("contractVersion", "ticket-clarification@0.1.0", "clientAnswerId", UUID.randomUUID(),
                        "expectedContextRevision", 0, "answers", List.of(Map.of("slot", "location.room", "value", "1304", "correction", false))))))
                .andExpect(status().isCreated());
    }

    private static UUID id(JsonNode ticket) { return UUID.fromString(ticket.path("id").asText()); }
    private static String path(JsonNode ticket) { return BASE + "/" + id(ticket); }
    private static RequestPostProcessor as(String subject, String... roles) {
        return jwt().jwt(token -> token.subject(subject).claim("groups", Arrays.stream(roles)
                .map(role -> "/workspaces/lab-a/" + role).toList()));
    }
}
