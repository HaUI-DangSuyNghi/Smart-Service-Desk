package vn.haui.dx.apps.servicedesk;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import vn.haui.dx.core.common.CoreException;
import vn.haui.dx.core.workflow.api.WorkflowService;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@org.springframework.context.annotation.Import(vn.haui.dx.core.workflow.internal.WorkflowStateFixture.class)
@SpringBootTest
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "FILE_TEST_JDBC_URL", matches = ".+/file_storage")
class TicketIntegrationTest {
    private static final String BASE = "/api/apps/servicedesk/v1/workspaces/lab-a/tickets";
    private static final Path STORAGE = Path.of(System.getProperty("java.io.tmpdir"), "ticket-test-" + UUID.randomUUID());

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", () -> System.getenv("FILE_TEST_JDBC_URL"));
        properties.add("spring.datasource.username", () -> "workflow");
        properties.add("spring.datasource.password", () -> System.getenv("FILE_TEST_DB_PASSWORD"));
        properties.add("dx.storage.root", STORAGE::toString);
        properties.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri", () -> "http://127.0.0.1:1/jwks");
    }

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired vn.haui.dx.core.workflow.internal.WorkflowStateFixture engineState;
    @SpyBean WorkflowService workflow;


    private RequestPostProcessor as(String subject, String workspace, String... roles) {
        return jwt().jwt(token -> token.subject(subject).claim("groups", Arrays.stream(roles)
            .map(role -> "/workspaces/" + workspace + "/" + role).toList()));
    }

    private String payload(UUID key, String title) throws Exception {
        return json.writeValueAsString(Map.of("clientRequestId", key, "title", title,
            "description", "Synthetic support request\nNo sensitive data."));
    }

    private ResultActions submit(String subject, UUID key, String title, String... roles) throws Exception {
        return mvc.perform(post(BASE).with(as(subject, "lab-a", roles)).contentType("application/json")
            .content(payload(key, title)));
    }

    private JsonNode body(ResultActions result) throws Exception {
        return json.readTree(result.andReturn().getResponse().getContentAsString());
    }

    private JsonNode create(String subject, String title, String... roles) throws Exception {
        return body(submit(subject, UUID.randomUUID(), title, roles).andExpect(status().isCreated()));
    }

    private long[] counts() {
        return java.util.stream.LongStream.concat(java.util.stream.LongStream.of(
            jdbc.queryForObject("select count(*) from servicedesk_ticket", Long.class)),
            java.util.Arrays.stream(engineState.counts())).toArray();
    }

    private JsonNode statistics(String workspace, String subject, String query, String... roles) throws Exception {
        return body(mvc.perform(get(BASE.replace("lab-a", workspace) + "/statistics").param("q", query)
            .with(as(subject, workspace, roles))).andExpect(status().isOk()));
    }

    private void metrics(JsonNode result, long handling, long confirming, long completed, long unclassified) {
        assertEquals(handling + confirming + completed + unclassified, result.path("total").asLong());
        assertEquals(handling, result.path("awaitingHandling").asLong());
        assertEquals(confirming, result.path("awaitingConfirmation").asLong());
        assertEquals(completed, result.path("completed").asLong());
        assertEquals(unclassified, result.path("unclassified").asLong());
    }

    @Test void statisticsAreEmptyAndRequireAnExplicitWorkspaceRole() throws Exception {
        String path = BASE + "/statistics";
        mvc.perform(get(path)).andExpect(status().isUnauthorized());
        mvc.perform(get(path).with(as("foreign", "lab-b", "coordinator"))).andExpect(status().isForbidden());
        mvc.perform(get(path).with(as("observer", "lab-a", "observer"))).andExpect(status().isForbidden());
        mvc.perform(get(path).with(jwt().jwt(token -> token.subject("admin")
            .claim("realm_access", Map.of("roles", List.of("platform-admin")))))).andExpect(status().isForbidden());
        var result = statistics("lab-a", "empty", UUID.randomUUID().toString(), "requester");
        metrics(result, 0, 0, 0, 0);
        assertEquals("lab-a", result.path("workspace").asText());
        assertEquals("own", result.path("visibility").asText());
    }

    @Test void statisticsReuseListVisibilityAndLiteralSearchWithoutCrossWorkspaceCounts() throws Exception {
        String marker = "stats-" + UUID.randomUUID();
        create("stats-owner", marker + " %_ Alpha", "requester");
        create("stats-owner", marker + " %_ Beta", "requester");
        create("stats-other", marker + " Other", "requester");
        mvc.perform(post(BASE.replace("lab-a", "lab-b")).with(as("stats-owner", "lab-b", "requester"))
            .contentType("application/json").content(payload(UUID.randomUUID(), marker)))
            .andExpect(status().isCreated());
        for (String role : List.of("requester", "agent", "coordinator")) {
            var stats = statistics("lab-a", "stats-owner", marker.toUpperCase(), role);
            metrics(stats, role.equals("requester") ? 2 : 3, 0, 0, 0);
            assertEquals(role.equals("requester") ? "own" : "workspace", stats.path("visibility").asText());
            var list = body(mvc.perform(get(BASE).param("q", marker).param("size", "1")
                .with(as("stats-owner", "lab-a", role))).andExpect(status().isOk()));
            assertEquals(list.get("total"), stats.get("total"));
        }
        metrics(statistics("lab-a", "stats-agent", marker + " %_", "agent"), 2, 0, 0, 0);
        metrics(statistics("lab-b", "stats-owner", marker, "coordinator"), 1, 0, 0, 0);
        metrics(statistics("lab-a", "stats-hidden", marker, "requester"), 0, 0, 0, 0);
        metrics(statistics("lab-a", "stats-agent", "' OR 1=1 --", "agent"), 0, 0, 0, 0);
    }

    @Test void statisticsAndListShareHalfOpenCreationTimeBoundsAndOffsets() throws Exception {
        String marker = "time-" + UUID.randomUUID();
        String[] dates = {"2026-01-01T00:00:00Z", "2026-01-01T00:00:00.000001Z", "2026-01-02T00:00:00Z"};
        for (String date : dates) {
            var ticket = create("time-owner", marker, "requester");
            jdbc.update("update servicedesk_ticket set created_at=cast(? as timestamptz) where id=?",
                date, UUID.fromString(ticket.get("id").asText()));
        }
        for (String route : List.of(BASE, BASE + "/statistics")) {
            mvc.perform(get(route).with(as("time-owner", "lab-a", "requester")).param("q", marker)
                .param("from", "2026-01-01T07:00:00+07:00").param("to", dates[2]))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(2));
            mvc.perform(get(route).with(as("time-owner", "lab-a", "requester")).param("q", marker)
                .param("from", dates[1]).param("to", dates[2]))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(1));
            mvc.perform(get(route).with(as("time-owner", "lab-a", "requester")).param("q", marker).param("to", dates[0]))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(0));
            mvc.perform(get(route).with(as("time-owner", "lab-a", "requester")).param("q", marker).param("from", dates[2]))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(1));
        }
    }

    @Test void statisticsAndListRejectInvalidFilters() throws Exception {
        for (String route : List.of(BASE, BASE + "/statistics")) {
            for (String invalid : List.of("not-a-date", "2026-01-01", "2026-01-01T00:00:00.000000001Z", "0000-01-01T00:00:00Z")) {
                mvc.perform(get(route).with(as("invalid-stats", "lab-a", "requester")).param("from", invalid))
                    .andExpect(status().isBadRequest());
                mvc.perform(get(route).with(as("invalid-stats", "lab-a", "requester")).param("to", invalid))
                    .andExpect(status().isBadRequest());
            }
            for (String from : List.of("2026-01-02T00:00:00Z", "2026-01-03T00:00:00Z"))
                mvc.perform(get(route).with(as("invalid-stats", "lab-a", "requester"))
                    .param("from", from).param("to", "2026-01-02T00:00:00Z")).andExpect(status().isBadRequest());
            for (String query : List.of("x".repeat(201), "bad\nquery"))
                mvc.perform(get(route).with(as("invalid-stats", "lab-a", "requester")).param("q", query))
                    .andExpect(status().isBadRequest());
        }
    }

    @Test void statisticsDoNotMislabelSuspendedDeletedOrMissingHistoryAsCompleted() throws Exception {
        String marker = "unknown-" + UUID.randomUUID();
        var ticket = create("unknown-owner", marker, "requester");
        String process = ticket.path("process").path("processInstanceId").asText();
        engineState.suspend(process);
        metrics(statistics("lab-a", "unknown-owner", marker, "requester"), 0, 0, 0, 1);
        engineState.activate(process);
        metrics(statistics("lab-a", "unknown-owner", marker, "requester"), 1, 0, 0, 0);
        engineState.cancel(process);
        metrics(statistics("lab-a", "unknown-owner", marker, "requester"), 0, 0, 0, 1);
        engineState.removeHistory(process);
        metrics(statistics("lab-a", "unknown-owner", marker, "requester"), 0, 0, 0, 1);
    }

    @Test void statisticsIgnoreStandaloneCoreProcessesAndDetectScopeMismatch() throws Exception {
        String marker = "scope-" + UUID.randomUUID();
        var previous = SecurityContextHolder.getContext();
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new JwtAuthenticationToken(Jwt.withTokenValue("statistics-fixture")
            .header("alg", "none").subject("standalone-stats")
            .claim("groups", List.of("/workspaces/lab-a/requester")).build()));
        SecurityContextHolder.setContext(context);
        try {
            workflow.start("lab-a", ServiceDeskProcess.KEY, marker, Map.of());
        } finally { SecurityContextHolder.setContext(previous); }
        metrics(statistics("lab-a", "standalone-stats", null, "requester"), 0, 0, 0, 0);
        var ticket = create("scope-stats", marker, "requester");
        String process = ticket.path("process").path("processInstanceId").asText();
        engineState.changeWorkspace(process, "lab-b");
        try {
            metrics(statistics("lab-a", "scope-stats", marker, "requester"), 0, 0, 0, 1);
            metrics(statistics("lab-b", "scope-stats", marker, "agent"), 0, 0, 0, 0);
        } finally { engineState.changeWorkspace(process, "lab-a"); }
    }

    @Test void createPersistsCanonicalProcessAndRealAuditWithoutContent() throws Exception {
        UUID trace = UUID.randomUUID();
        UUID key = UUID.randomUUID();
        var ticket = body(mvc.perform(post(BASE).with(as("ticket-owner", "lab-a", "requester"))
            .header("X-Request-ID", trace).contentType("application/json").content(payload(key, "Network support")))
            .andExpect(status().isCreated()).andExpect(header().string("X-Request-ID", trace.toString())));
        String id = ticket.get("id").asText();
        assertEquals("ticket-owner", ticket.get("creator").asText());
        assertEquals("lab-a", ticket.get("workspace").asText());
        assertEquals(key.toString(), ticket.get("clientRequestId").asText());
        assertEquals(id, ticket.path("process").path("businessKey").asText());
        assertEquals("service-desk", ticket.path("process").path("processKey").asText());
        assertFalse(ticket.has("status"));
        mvc.perform(get(BASE + "/" + id).with(as("ticket-owner", "lab-a", "requester")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.createdAt").isString());
        var audit = jdbc.queryForMap("select actor,request_id,details::text as details from audit_event "
            + "where resource=? and action='servicedesk.ticket.create'", id);
        assertEquals("ticket-owner", audit.get("actor"));
        assertEquals(trace.toString(), audit.get("request_id"));
        assertEquals(Map.of("processInstanceId", ticket.path("process").path("processInstanceId").asText()),
            json.readValue((String) audit.get("details"), Map.class));
        assertTrue(engineState.matches(ticket.path("process").path("processInstanceId").asText(),
            "lab-a", "ticket-owner", id));
    }

    @Test void authenticationRolesAndWorkspaceIsolationAreEnforced() throws Exception {
        var ticket = create("isolation-owner", "Isolation", "requester");
        String path = BASE + "/" + ticket.get("id").asText();
        for (String route : List.of(BASE, path, path + "/progress")) {
            mvc.perform(get(route)).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
            mvc.perform(get(route).with(as("foreign", "lab-b", "coordinator"))).andExpect(status().isForbidden());
        }
        mvc.perform(post(BASE).contentType("application/json").content(payload(UUID.randomUUID(), "Denied")))
            .andExpect(status().isUnauthorized());
        mvc.perform(get(BASE).header("Authorization", "Bearer invalid")).andExpect(status().isUnauthorized());
        mvc.perform(get(BASE).with(jwt().jwt(token -> token.subject("platform-admin")
            .claim("realm_access", Map.of("roles", List.of("platform-admin")))))).andExpect(status().isForbidden());
        mvc.perform(get(BASE).with(as("unknown", "lab-a", "observer"))).andExpect(status().isForbidden());
        mvc.perform(post(BASE).with(as("foreign", "lab-b", "agent")).contentType("application/json")
            .content(payload(UUID.randomUUID(), "Denied"))).andExpect(status().isForbidden());
        mvc.perform(get(path.replace("lab-a", "lab-b")).with(as("foreign", "lab-b", "agent")))
            .andExpect(status().isNotFound());
    }

    @Test void listAndDetailHaveIdenticalVisibilityAndStablePagination() throws Exception {
        String marker = "list-" + UUID.randomUUID();
        var own = create("list-owner", marker + " Alpha", "requester");
        create("list-owner", marker + " Beta", "requester");
        var hidden = create("list-other", marker + " Other", "requester");
        mvc.perform(get(BASE).param("q", marker.toUpperCase()).param("size", "1").with(as("list-owner", "lab-a", "requester")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(2)).andExpect(jsonPath("$.items.length()").value(1));
        var first = body(mvc.perform(get(BASE).param("q", marker).param("size", "1")
            .with(as("list-owner", "lab-a", "requester"))).andExpect(status().isOk()));
        var second = body(mvc.perform(get(BASE).param("q", marker).param("size", "1").param("page", "1")
            .with(as("list-owner", "lab-a", "requester"))).andExpect(status().isOk()));
        assertNotEquals(first.path("items").get(0).get("id"), second.path("items").get(0).get("id"));
        for (String role : List.of("agent", "coordinator"))
            mvc.perform(get(BASE).param("q", marker).with(as("staff", "lab-a", role)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(3));
        mvc.perform(get(BASE + "/" + hidden.get("id").asText()).with(as("list-owner", "lab-a", "requester")))
            .andExpect(status().isNotFound());
        mvc.perform(get(BASE + "/" + own.get("id").asText()).with(as("staff", "lab-a", "agent")))
            .andExpect(status().isOk());
        mvc.perform(get(BASE).param("q", "%_' OR 1=1 --").with(as("list-owner", "lab-a", "requester")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(0));
    }

    @Test void invalidAndSpoofedPayloadsNeverWrite() throws Exception {
        long[] before = counts();
        var valid = json.readTree(payload(UUID.randomUUID(), "Valid"));
        for (String field : List.of("owner", "creator", "workspace", "roles", "process", "status")) {
            var spoofed = valid.deepCopy();
            ((com.fasterxml.jackson.databind.node.ObjectNode) spoofed).put(field, "spoof");
            mvc.perform(post(BASE).with(as("invalid", "lab-a", "requester")).contentType("application/json")
                .content(spoofed.toString())).andExpect(status().isBadRequest());
        }
        for (String invalid : List.of("{}", "null", "{", "{\"clientRequestId\":\"bad\"}",
                payload(UUID.randomUUID(), " "), payload(UUID.randomUUID(), "x".repeat(201)),
                payload(UUID.randomUUID(), "bad\nline"), payload(UUID.randomUUID(), "\u2003")))
            mvc.perform(post(BASE).with(as("invalid", "lab-a", "requester")).contentType("application/json")
                .content(invalid)).andExpect(status().isBadRequest());
        for (String description : List.of(" ", "x".repeat(4001), "bad\u0000text")) {
            var invalid = valid.deepCopy();
            ((com.fasterxml.jackson.databind.node.ObjectNode) invalid).put("description", description);
            mvc.perform(post(BASE).with(as("invalid", "lab-a", "requester")).contentType("application/json")
                .content(invalid.toString())).andExpect(status().isBadRequest());
        }
        for (String query : List.of("size=0", "size=101", "page=-1", "page=bad"))
            mvc.perform(get(BASE + "?" + query).with(as("invalid", "lab-a", "requester"))).andExpect(status().isBadRequest());
        mvc.perform(get(BASE).param("q", "x".repeat(201)).with(as("invalid", "lab-a", "requester")))
            .andExpect(status().isBadRequest());
        mvc.perform(get(BASE + "/bad-id").with(as("invalid", "lab-a", "requester"))).andExpect(status().isBadRequest());
        assertArrayEquals(before, counts());
    }

    @Test void duplicateSubmissionRollsBackSecondEngineAndAudit() throws Exception {
        UUID key = UUID.randomUUID();
        submit("duplicate", key, "First", "requester").andExpect(status().isCreated());
        long[] before = counts();
        submit("duplicate", key, "Changed", "requester").andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("CONFLICT"));
        assertArrayEquals(before, counts());
        submit("different-creator", key, "Independent", "requester").andExpect(status().isCreated());
        mvc.perform(post(BASE.replace("lab-a", "lab-b")).with(as("duplicate", "lab-b", "requester"))
            .contentType("application/json").content(payload(key, "Independent workspace"))).andExpect(status().isCreated());
    }

    @Test void concurrentSubmissionHasOneTicketProcessAndAudit() throws Exception {
        UUID key = UUID.randomUUID();
        long[] before = counts();
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(2);
        try {
            java.util.concurrent.Callable<Integer> action = () -> {
                ready.countDown();
                assertTrue(start.await(15, TimeUnit.SECONDS));
                return submit("concurrent", key, "Concurrent", "requester").andReturn().getResponse().getStatus();
            };
            var first = pool.submit(action);
            var second = pool.submit(action);
            assertTrue(ready.await(15, TimeUnit.SECONDS));
            start.countDown();
            var results = List.of(first.get(30, TimeUnit.SECONDS), second.get(30, TimeUnit.SECONDS));
            assertEquals(List.of(201, 409), results.stream().sorted().toList());
        } finally { start.countDown(); pool.shutdownNow(); }
        long[] after = counts();
        assertArrayEquals(new long[] {1, 1, 1, 1, 2}, java.util.stream.IntStream.range(0, before.length)
            .mapToLong(index -> after[index] - before[index]).toArray());
    }

    @Test void workflowStartFailureRollsBack() throws Exception {
        long[] before = counts();
        doThrow(CoreException.conflict("Workflow unavailable"))
            .when(workflow).start(eq("lab-a"), eq(ServiceDeskProcess.KEY), anyString(), anyMap());
        submit("start-failure", UUID.randomUUID(), "Failure", "requester").andExpect(status().isConflict());
        assertArrayEquals(before, counts());
    }

    @Test void failureAfterRealWorkflowStartAlsoRollsBack() throws Exception {
        long[] before = counts();
        doAnswer(invocation -> {
            invocation.callRealMethod();
            throw CoreException.conflict("Failure after start");
        }).when(workflow).start(eq("lab-a"), eq(ServiceDeskProcess.KEY), anyString(), anyMap());
        submit("partial-failure", UUID.randomUUID(), "Failure", "requester").andExpect(status().isConflict());
        assertArrayEquals(before, counts());
    }

    @Test void auditFailureRollsBackTicketAndRealEngine() throws Exception {
        long[] before = counts();
        jdbc.execute("create function ticket_test_reject_audit() returns trigger language plpgsql as $$ begin "
            + "if new.action='servicedesk.ticket.create' then raise exception 'test rejection' using errcode='23514'; "
            + "end if; return new; end $$");
        jdbc.execute("create trigger ticket_test_reject before insert on audit_event "
            + "for each row execute function ticket_test_reject_audit()");
        try {
            submit("audit-failure", UUID.randomUUID(), "Failure", "requester").andExpect(status().isConflict());
            assertArrayEquals(before, counts());
        } finally {
            jdbc.execute("drop trigger ticket_test_reject on audit_event");
            jdbc.execute("drop function ticket_test_reject_audit()");
        }
    }

    @Test void independentHandlingReworkAndInitiatorConfirmationUseRealWorkflow() throws Exception {
        var ticket = create("workflow-owner", "Workflow", "requester", "agent");
        String ticketPath = BASE + "/" + ticket.get("id").asText();
        String core = "/api/core/v1/workspaces/lab-a/instances/" + ticket.path("process").path("processInstanceId").asText();
        mvc.perform(get(ticketPath + "/progress").with(as("handler", "lab-a", "agent"))).andExpect(status().isNotFound());
        mvc.perform(get(ticketPath + "/progress").with(as("coordinator", "lab-a", "coordinator"))).andExpect(status().isOk());
        for (String confirmation : List.of("rework", "accepted")) {
            metrics(statistics("lab-a", "workflow-owner", "Workflow", "requester"), 1, 0, 0, 0);
            var progress = body(mvc.perform(get(ticketPath + "/progress").with(as("workflow-owner", "lab-a", "requester")))
                .andExpect(status().isOk()));
            String task = progress.path("currentTasks").get(0).get("id").asText();
            assertEquals("handle", progress.path("currentTasks").get(0).get("key").asText());
            mvc.perform(post(core + "/tasks/" + task + "/claim").with(as("workflow-owner", "lab-a", "agent")))
                .andExpect(status().isForbidden());
            mvc.perform(post(core + "/tasks/" + task + "/claim").with(as("coordinator", "lab-a", "coordinator")))
                .andExpect(status().isForbidden());
            mvc.perform(post(core + "/tasks/" + task + "/claim").with(as("handler", "lab-a", "agent")))
                .andExpect(status().isOk());
            metrics(statistics("lab-a", "workflow-owner", "Workflow", "requester"), 1, 0, 0, 0);
            mvc.perform(post(core + "/tasks/" + task + "/complete").with(as("handler", "lab-a", "agent"))
                .contentType("application/json").content("{\"expectedTaskKey\":\"handle\",\"outcome\":\"resolved\"}"))
                .andExpect(status().isNoContent());
            metrics(statistics("lab-a", "workflow-owner", "Workflow", "requester"), 0, 1, 0, 0);
            progress = body(mvc.perform(get(ticketPath + "/progress").with(as("workflow-owner", "lab-a", "requester")))
                .andExpect(status().isOk()));
            String confirm = progress.path("currentTasks").get(0).get("id").asText();
            assertEquals("confirm", progress.path("currentTasks").get(0).get("key").asText());
            mvc.perform(post(core + "/tasks/" + confirm + "/complete").with(as("handler", "lab-a", "agent"))
                .contentType("application/json").content("{\"expectedTaskKey\":\"confirm\",\"outcome\":\"accepted\"}"))
                .andExpect(status().isForbidden());
            mvc.perform(post(core + "/tasks/" + confirm + "/complete").with(as("workflow-owner", "lab-a", "requester"))
                .contentType("application/json").content(json.writeValueAsString(Map.of("expectedTaskKey", "confirm", "outcome", confirmation))))
                .andExpect(status().isNoContent());
        }
        mvc.perform(get(ticketPath + "/progress").with(as("workflow-owner", "lab-a", "requester")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.ended").value(true))
            .andExpect(jsonPath("$.steps.length()").value(4)).andExpect(jsonPath("$.currentTasks").isEmpty());
        metrics(statistics("lab-a", "workflow-owner", "Workflow", "requester"), 0, 0, 1, 0);
        assertEquals(1L, jdbc.queryForObject("select count(*) from audit_event where action='servicedesk.ticket.create' "
            + "and resource=?", Long.class, ticket.get("id").asText()));
    }
}
