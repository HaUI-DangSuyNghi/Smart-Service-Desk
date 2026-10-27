package vn.haui.dx.apps.servicedesk.intake;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import vn.haui.dx.apps.servicedesk.ServiceDeskProcess;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"serviceDesk.intake.new-tickets=true", "serviceDesk.intake.true-wait=true",
        "service-desk.background.scheduled=false"})
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "FILE_TEST_JDBC_URL", matches = ".+/file_storage")
class IntakeWorkflowIntegrationTest {
    private static final String BASE = "/api/apps/servicedesk/v1/workspaces/wait-a/tickets";
    private static final String TASKS = "/api/hpdi/v1/workspaces/wait-a/tasks/";
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired vn.haui.dx.core.identity.api.WorkspaceDirectory directory;

    @org.junit.jupiter.api.BeforeEach
    void availableSyntheticAgent() throws Exception {
        org.mockito.Mockito.when(directory.members("wait-a")).thenReturn(List.of(new vn.haui.dx.core.identity.api.DirectoryMember(
                "agent", "Synthetic agent", null, java.util.Set.of(vn.haui.dx.core.identity.api.WorkspaceRole.AGENT))));
        org.mockito.Mockito.when(directory.supportGroupMembers("wait-a", "hardware")).thenReturn(java.util.Set.of("agent"));
        mvc.perform(put("/api/hpdi/v1/workspaces/wait-a/duty/me").with(as("agent", "agent")).contentType("application/json")
                .content("{\"onDuty\":true}")).andExpect(status().isOk());
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> System.getenv("FILE_TEST_JDBC_URL"));
        registry.add("spring.datasource.username", () -> "workflow");
        registry.add("spring.datasource.password", () -> System.getenv("FILE_TEST_DB_PASSWORD"));
        registry.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri", () -> "http://127.0.0.1:1/jwks");
    }

    @Test
    void v2HandleKeepsDutyRoutingAndReadOnlyAssignmentSuggestion() throws Exception {
        var ticket = create();
        release(ticket, UUID.randomUUID());
        mvc.perform(get(path(ticket) + "/assignment-suggestion").param("category", "HARDWARE").param("priority", "HIGH")
                .with(as("coordinator", "coordinator"))).andExpect(status().isOk());
        review(ticket, state(ticket).path("version").asLong(), 200);
        String handle = getTicket(ticket).path("taskId").asText();
        mvc.perform(get(TASKS + handle + "/assignment-suggestion").with(as("coordinator", "coordinator"))).andExpect(status().isOk());
        mvc.perform(put("/api/hpdi/v1/workspaces/wait-a/duty/me").with(as("agent", "agent")).contentType("application/json")
                .content("{\"onDuty\":false}")).andExpect(status().isOk());
        mvc.perform(post(TASKS + handle + "/claim").with(as("agent", "agent"))).andExpect(status().isConflict());
        assertEquals(0L, jdbc.queryForObject("select count(*) from servicedesk_assignment_event where process_id=?", Long.class,
                ticket.path("process").path("processInstanceId").asText()));
    }

    @Test
    void createWaitsAtIntakeAndIsAbsentFromReviewQueue() throws Exception {
        var ticket = create();
        assertEquals(ServiceDeskProcess.INTAKE_KEY, ticket.path("process").path("processKey").asText());
        assertEquals("INTAKE", ticket.path("state").asText());
        assertFalse(state(ticket).path("reviewTaskMayExist").asBoolean());
        mvc.perform(get(BASE).param("state", "AWAITING_REVIEW").param("q", ticket.path("title").asText()).with(as("coordinator", "coordinator")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(0));
        assertEquals(0L, jdbc.queryForObject("select count(*) from servicedesk_review where ticket_id=?", Long.class, id(ticket)));
        assertEquals(0L, slaCount(ticket));
        assertTrue(getTicket(ticket).path("sla").isNull());
    }

    @Test
    void manualReleaseCreatesExactlyOneReviewInTheSameProcess() throws Exception {
        var ticket = create();
        UUID operation = UUID.randomUUID();
        release(ticket, operation);
        var reviewed = getTicket(ticket);
        assertEquals("AWAITING_REVIEW", reviewed.path("state").asText());
        assertNotEquals(ticket.path("taskId"), reviewed.path("taskId"));
        assertEquals(ticket.path("process"), reviewed.path("process"));
        assertTrue(state(ticket).path("reviewTaskMayExist").asBoolean());
        release(ticket, operation);
        assertEquals(reviewed.path("taskId"), getTicket(ticket).path("taskId"));
    }

    @Test
    void rawTaskCompletionCannotReleaseIntakeOrForgeDuplicate() throws Exception {
        var ticket = create();
        String task = ticket.path("taskId").asText();
        mvc.perform(post(TASKS + task + "/claim").with(as("coordinator", "coordinator"))).andExpect(status().isOk());
        for (String outcome : List.of("ready", "duplicate")) {
            mvc.perform(post(TASKS + task + "/complete").with(as("coordinator", "coordinator"))
                            .contentType("application/json").content("{\"expectedTaskKey\":\"intake\",\"outcome\":\"" + outcome + "\"}"))
                    .andExpect(status().isConflict());
        }
        assertEquals("INTAKE", getTicket(ticket).path("state").asText());
    }

    @Test
    void forgedReviewBeforeIntakeReleaseIsRejected() throws Exception {
        var ticket = create();
        review(ticket, 0, 409);
        assertEquals("INTAKE", getTicket(ticket).path("state").asText());
        assertEquals(0L, slaCount(ticket));
    }

    @Test
    void clarificationWhileWaitingRemainsPreReviewAndInvalidatesOldCas() throws Exception {
        var ticket = create();
        mvc.perform(post(path(ticket) + "/clarifications").with(as("owner", "requester")).contentType("application/json")
                .content(json.writeValueAsString(Map.of("contractVersion", "ticket-clarification@0.1.0", "clientAnswerId", UUID.randomUUID(),
                        "expectedContextRevision", 0, "answers", List.of(Map.of("slot", "location.room", "value", "1304", "correction", false))))))
                .andExpect(status().isCreated());
        var state = state(ticket);
        assertEquals("NEW", state.path("status").asText());
        assertFalse(state.path("lateNotice").asBoolean());
        assertFalse(state.path("reviewTaskMayExist").asBoolean());
    }

    @Test
    void reviewHandleConfirmReworkReturnsToIntakeWithoutSecondProcess() throws Exception {
        var ticket = create();
        release(ticket, UUID.randomUUID());
        assertEquals(0L, slaCount(ticket));
        var state = state(ticket);
        review(ticket, state.path("version").asLong(), 200);
        assertEquals(1L, slaCount(ticket));
        assertFalse(getTicket(ticket).path("sla").isNull());
        var handle = getTicket(ticket).path("taskId").asText();
        mvc.perform(post(TASKS + handle + "/claim").with(as("agent", "agent"))).andExpect(status().isOk());
        mvc.perform(post(TASKS + handle + "/start").with(as("agent", "agent"))).andExpect(status().isOk());
        assertNotNull(jdbc.queryForObject("select first_started_at from servicedesk_sla where handle_task_id=?", java.sql.Timestamp.class, handle));
        mvc.perform(post(TASKS + handle + "/pause").with(as("agent", "agent")).contentType("application/json")
                .content("{\"reason\":\"WAITING_REQUESTER\"}")).andExpect(status().isOk()).andExpect(jsonPath("$.sla.clockPaused").value(true));
        mvc.perform(post(TASKS + handle + "/resume").with(as("agent", "agent")))
                .andExpect(status().isOk());
        assertNull(jdbc.queryForObject("select paused_at from servicedesk_sla where handle_task_id=?", java.sql.Timestamp.class, handle));
        mvc.perform(post(TASKS + handle + "/complete").with(as("agent", "agent")).contentType("application/json")
                .content("{\"expectedTaskKey\":\"handle\",\"outcome\":\"resolved\"}")).andExpect(status().isNoContent());
        var confirm = getTicket(ticket).path("taskId").asText();
        mvc.perform(post(TASKS + confirm + "/complete").with(as("owner", "requester")).contentType("application/json")
                .content("{\"expectedTaskKey\":\"confirm\",\"outcome\":\"rework\"}")).andExpect(status().isNoContent());
        assertEquals("INTAKE", getTicket(ticket).path("state").asText());
        assertEquals(ticket.path("process"), getTicket(ticket).path("process"));
        assertEquals(2, state(ticket).path("reviewRound").asInt());
        assertEquals("NEW", state(ticket).path("status").asText());
        assertFalse(state(ticket).path("reviewTaskMayExist").asBoolean());
        assertEquals(1L, slaCount(ticket));
        assertTrue(getTicket(ticket).path("sla").isNull());
        var previous = jdbc.queryForMap("select * from servicedesk_sla where handle_task_id=?", handle);
        assertNotNull(previous.get("resolved_at"));
        release(ticket, UUID.randomUUID(), state(ticket).path("version").asLong());
        assertTrue(getTicket(ticket).path("sla").isNull());
        review(ticket, state(ticket).path("version").asLong(), 200);
        assertEquals(2L, slaCount(ticket));
        assertEquals(previous, jdbc.queryForMap("select * from servicedesk_sla where handle_task_id=?", handle));
        assertFalse(getTicket(ticket).path("sla").isNull());
    }

    @Test
    void slaFailureRollsBackIntakeApprovalFeedbackAndWorkflow() throws Exception {
        var ticket = create();
        release(ticket, UUID.randomUUID());
        var before = state(ticket);
        var task = getTicket(ticket).path("taskId");
        jdbc.execute("create function intake_reject_sla() returns trigger language plpgsql as $$ begin raise exception 'SLA rejected' using errcode='23514'; end $$");
        jdbc.execute("create trigger intake_reject_sla before insert on servicedesk_sla for each row execute function intake_reject_sla()");
        try {
            review(ticket, before.path("version").asLong(), 409);
            assertEquals(before, state(ticket));
            assertEquals(task, getTicket(ticket).path("taskId"));
            assertEquals(0L, slaCount(ticket));
            for (String table : List.of("servicedesk_review", "servicedesk_estimate_history", "servicedesk_ai_feedback"))
                assertEquals(0L, jdbc.queryForObject("select count(*) from " + table + " where ticket_id=?", Long.class, id(ticket)));
        } finally {
            jdbc.execute("drop trigger intake_reject_sla on servicedesk_sla");
            jdbc.execute("drop function intake_reject_sla()");
        }
        review(ticket, before.path("version").asLong(), 200);
        assertEquals(1L, slaCount(ticket));
    }

    private long slaCount(JsonNode ticket) {
        return jdbc.queryForObject("select count(*) from servicedesk_sla where ticket_id=?", Long.class, id(ticket));
    }

    private JsonNode create() throws Exception {
        return body(mvc.perform(post(BASE).with(as("owner", "requester")).contentType("application/json")
                .content(json.writeValueAsString(Map.of("clientRequestId", UUID.randomUUID(), "title", "Synthetic wait " + UUID.randomUUID(), "description", "Synthetic wait"))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
    }
    private JsonNode getTicket(JsonNode ticket) throws Exception {
        return body(mvc.perform(get(path(ticket)).with(as("owner", "requester")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }
    private JsonNode state(JsonNode ticket) throws Exception {
        return body(mvc.perform(get(path(ticket) + "/intake").with(as("owner", "requester")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }
    private void release(JsonNode ticket, UUID operation) throws Exception {
        release(ticket, operation, 0);
    }
    private void release(JsonNode ticket, UUID operation, long version) throws Exception {
        mvc.perform(post(path(ticket) + "/intake/manual-release").with(as("coordinator", "coordinator")).contentType("application/json")
                .content(json.writeValueAsString(Map.of("expectedContextRevision", 0, "expectedVersion", version, "operationId", operation, "reason", "Synthetic human disposition"))))
                .andExpect(status().isOk());
    }
    private void review(JsonNode ticket, long version, int expected) throws Exception {
        mvc.perform(post(path(ticket) + "/review").with(as("coordinator", "coordinator")).contentType("application/json")
                .content("{\"category\":\"HARDWARE\",\"priority\":\"HIGH\",\"assignee\":null,\"reason\":\"Synthetic review\","
                        + "\"expectedContextRevision\":0,\"expectedIntakeVersion\":" + version + "}"))
                .andExpect(status().is(expected));
    }
    private JsonNode body(String value) throws Exception { return json.readTree(value); }
    private static UUID id(JsonNode ticket) { return UUID.fromString(ticket.path("id").asText()); }
    private static String path(JsonNode ticket) { return BASE + "/" + id(ticket); }
    private static RequestPostProcessor as(String subject, String role) {
        return jwt().jwt(token -> token.subject(subject).claim("groups", List.of("/workspaces/wait-a/" + role)));
    }
}
