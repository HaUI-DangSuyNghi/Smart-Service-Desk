package vn.haui.dx.apps.servicedesk;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
    @SpyBean JdbcTemplate jdbc;
    @Autowired vn.haui.dx.core.workflow.internal.WorkflowStateFixture engineState;
    @SpyBean WorkflowService workflow;
    @SpyBean TicketReviewService reviewViews;
    @SpyBean vn.haui.dx.hpdi.process.api.ProcessCapability processViews;
    @Autowired vn.haui.dx.core.identity.api.WorkspaceDirectory directory;
    @SpyBean vn.haui.dx.hpdi.human.api.AgentDuty duty;
    @Autowired TicketSlaService slaService;

    @Test void slaApprovalSnapshotsCoordinatorDecisionAndLeavesUnreviewedAndForeignTicketsWithoutSla() throws Exception {
        var ticket = create("sla-owner", "SLA approval", "requester");
        assertTrue(ticket.path("sla").isNull());
        var reviewed = body(review(ticket, "sla-coordinator", null, null).andExpect(status().isOk()));
        assertEquals("HIGH", reviewed.path("classification").path("basePriority").asText());
        assertFalse(reviewed.path("sla").path("startBreached").asBoolean());
        assertFalse(reviewed.path("sla").path("resolveBreached").asBoolean());
        var id = UUID.fromString(ticket.path("id").asText());
        var state = slaService.read("lab-a", id);
        assertEquals("r5-v1", state.policy().version());
        assertEquals(60, state.policy().targets().get(state.base()).startMinutes());
        assertNull(slaService.read("lab-b", id));
        assertTrue(slaService.batch("lab-b", List.of(state.taskId())).isEmpty());
        var differentPolicy = new vn.haui.dx.hpdi.process.api.SlaPolicy("future-policy", state.policy().calendar(), state.policy().targets(), 1,
            vn.haui.dx.hpdi.process.api.SlaPolicy.Priority.CRITICAL, false);
        var restarted = new TicketSlaService(jdbc, json, differentPolicy, java.time.Clock.systemUTC(),
            mock(vn.haui.dx.hpdi.process.api.WorkScheduling.class), mock(vn.haui.dx.hpdi.human.api.ActivityJournal.class));
        assertEquals(state, restarted.read("lab-a", id));
        assertEquals(state.resolveBy().toEpochMilli(), jdbc.queryForObject("select due_date_ from act_ru_task where id_=?", java.sql.Timestamp.class, state.taskId()).getTime());
        long before = jdbc.queryForObject("select count(*) from audit_event", Long.class);
        mvc.perform(get(BASE + "/" + id).with(as("sla-owner", "lab-a", "requester"))).andExpect(status().isOk());
        assertEquals(before, jdbc.queryForObject("select count(*) from audit_event", Long.class));
    }

    @Test void slaPauseReasonsResumeAndCompletionKeepExecutionAndDeadlineClocksSeparate() throws Exception {
        var evidence = json.createObjectNode();
        for (var reason : List.of("WAITING_REQUESTER", "WAITING_PARTS", "WAITING_EXTERNAL", "OTHER")) {
            String worker = "sla-agent-" + reason;
            var ticket = create("sla-pause-owner", "SLA " + reason, "requester");
            var reviewed = body(review(ticket, "sla-coordinator", worker, null).andExpect(status().isOk()));
            String task = reviewed.path("taskId").asText();
            String route = "/api/hpdi/v1/workspaces/lab-a/tasks/" + task;
            evidence.set("review", reviewed);
            var started = body(mvc.perform(post(route + "/start").with(as(worker, "lab-a", "agent"))).andExpect(status().isOk()));
            evidence.set("start", started);
            var state = slaService.read("lab-a", UUID.fromString(ticket.path("id").asText()));
            assertNotNull(state.firstStarted());
            var paused = body(mvc.perform(post(route + "/pause").with(as(worker, "lab-a", "agent"))
                .contentType("application/json").content(json.writeValueAsString(Map.of("reason", reason, "note", "Pending test")))).andExpect(status().isOk()));
            evidence.set(reason, paused);
            var pausedState = slaService.read("lab-a", state.ticketId());
            assertEquals("WAITING_REQUESTER".equals(reason), pausedState.pausedAt() != null);
            var originalDue = pausedState.resolveBy();
            var firstRead = slaService.read("lab-a", state.ticketId());
            var secondRead = slaService.read("lab-a", state.ticketId());
            assertEquals(firstRead, secondRead);
            var resumed = body(mvc.perform(post(route + "/resume").with(as(worker, "lab-a", "agent"))).andExpect(status().isOk()));
            evidence.set("resume", resumed);
            var resumedState = slaService.read("lab-a", state.ticketId());
            assertNull(resumedState.pausedAt());
            assertFalse(resumedState.resolveBy().isBefore(originalDue));
            if (!"WAITING_REQUESTER".equals(reason)) assertEquals(originalDue, resumedState.resolveBy());
            mvc.perform(post(route + "/complete").with(as(worker, "lab-a", "agent")).contentType("application/json")
                .content("{\"expectedTaskKey\":\"handle\",\"outcome\":\"resolved\"}")).andExpect(status().isNoContent());
            var resolved = slaService.read("lab-a", state.ticketId());
            assertNotNull(resolved.resolvedAt());
            var frozen = resolved.project(resolved.resolvedAt().plus(Duration.ofDays(30)));
            assertEquals(resolved.firstStarted().isAfter(resolved.startBy()), frozen.startBreached());
            assertEquals(resolved.resolvedAt().isAfter(resolved.resolveBy()), frozen.resolveBreached());
            assertFalse(frozen.warning());
            evidence.set("resolved", body(mvc.perform(get(BASE + "/" + state.ticketId()).with(as("sla-pause-owner", "lab-a", "requester"))).andExpect(status().isOk())));
        }
        java.nio.file.Files.writeString(Path.of("target/r5-sla-http-evidence.json"), json.writerWithDefaultPrettyPrinter().writeValueAsString(evidence));
    }

    @Test void slaEscalationIsConcurrentIdempotentTransactionalAndKeepsFrozenDeadlines() throws Exception {
        var ticket = create("sla-escalation-owner", "Escalation", "requester");
        var reviewed = body(mvc.perform(post(BASE + "/" + ticket.path("id").asText() + "/review").with(as("sla-coordinator", "lab-a", "coordinator"))
            .contentType("application/json").content("{\"category\":\"OTHER\",\"priority\":\"LOW\",\"reason\":\"Approved low\",\"assignee\":null}")).andExpect(status().isOk()));
        String task = reviewed.path("taskId").asText();
        var id = UUID.fromString(ticket.path("id").asText());
        jdbc.update("update servicedesk_sla set approved_at=approved_at-interval '10 days',start_by=start_by-interval '10 days',resolve_by=resolve_by-interval '10 days' where ticket_id=?", id);
        var before = slaService.read("lab-a", id);
        jdbc.execute("create function sla_reject_escalation() returns trigger language plpgsql as $$ begin if new.action='servicedesk.sla.escalated' then raise exception 'sla escalation rejection' using errcode='23514'; end if; return new; end $$");
        jdbc.execute("create trigger sla_reject_escalation before insert on audit_event for each row execute function sla_reject_escalation()");
        try {
            assertThrows(RuntimeException.class, () -> slaService.reconcile("lab-a", before.processId(), task));
            assertEquals(before, slaService.read("lab-a", id));
            assertEquals(25, jdbc.queryForObject("select priority_ from act_ru_task where id_=?", Integer.class, task));
        } finally { jdbc.execute("drop trigger sla_reject_escalation on audit_event"); jdbc.execute("drop function sla_reject_escalation()"); }
        var pool = Executors.newFixedThreadPool(2);
        try {
            var first = pool.submit(() -> slaService.reconcile("lab-a", before.processId(), task));
            var second = pool.submit(() -> slaService.reconcile("lab-a", before.processId(), task));
            first.get(30, TimeUnit.SECONDS); second.get(30, TimeUnit.SECONDS);
        } finally { pool.shutdownNow(); }
        slaService.reconcile("lab-a", before.processId(), task);
        var after = slaService.read("lab-a", id);
        assertEquals(vn.haui.dx.hpdi.process.api.SlaPolicy.Priority.LOW, after.base());
        assertEquals(vn.haui.dx.hpdi.process.api.SlaPolicy.Priority.MEDIUM, after.priority());
        assertEquals(before.startBy(), after.startBy()); assertEquals(before.resolveBy(), after.resolveBy());
        assertEquals(50, jdbc.queryForObject("select priority_ from act_ru_task where id_=?", Integer.class, task));
        assertEquals(1, jdbc.queryForObject("select count(*) from audit_event where action='servicedesk.sla.escalated' and resource=?", Integer.class, id.toString()));
        mvc.perform(get(BASE + "/" + id).with(as("sla-escalation-owner", "lab-a", "requester"))).andExpect(status().isOk())
            .andExpect(jsonPath("$.classification.priority").value("MEDIUM")).andExpect(jsonPath("$.classification.basePriority").value("LOW"))
            .andExpect(jsonPath("$.classification.escalated").value(true));
    }

    @Test void slaStartAuditFailureRollsBackNativeExecutionAndSlaTimestamp() throws Exception {
        var ticket = create("sla-rollback-owner", "SLA rollback", "requester");
        String worker = "sla-rollback-agent";
        var reviewed = body(review(ticket, "sla-coordinator", worker, null).andExpect(status().isOk()));
        String task = reviewed.path("taskId").asText();
        jdbc.execute("create function sla_reject_audit() returns trigger language plpgsql as $$ begin if new.action='servicedesk.sla.start' then raise exception 'sla rejection' using errcode='23514'; end if; return new; end $$");
        jdbc.execute("create trigger sla_reject_audit before insert on audit_event for each row execute function sla_reject_audit()");
        try {
            mvc.perform(post("/api/hpdi/v1/workspaces/lab-a/tasks/" + task + "/start").with(as(worker, "lab-a", "agent"))).andExpect(status().isConflict());
            assertNull(slaService.read("lab-a", UUID.fromString(ticket.path("id").asText())).firstStarted());
            mvc.perform(get("/api/hpdi/v1/workspaces/lab-a/queue").with(as(worker, "lab-a", "agent"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].workState").value("NOT_STARTED"));
        } finally { jdbc.execute("drop trigger sla_reject_audit on audit_event"); jdbc.execute("drop function sla_reject_audit()"); }
    }

    @Test void slaLateStartAndResolutionPersistTheirOwnBreachResults() throws Exception {
        String worker = "sla-late-agent";
        var ticket = create("sla-late-owner", "Late SLA", "requester");
        var reviewed = body(review(ticket, "sla-coordinator", worker, null).andExpect(status().isOk()));
        var id = UUID.fromString(ticket.path("id").asText());
        String task = reviewed.path("taskId").asText(), route = "/api/hpdi/v1/workspaces/lab-a/tasks/" + task;
        jdbc.update("update servicedesk_sla set approved_at=approved_at-interval '30 days',start_by=start_by-interval '30 days',resolve_by=resolve_by-interval '30 days' where ticket_id=?", id);
        mvc.perform(post(route + "/start").with(as(worker, "lab-a", "agent"))).andExpect(status().isOk())
            .andExpect(jsonPath("$.sla.startBreached").value(true)).andExpect(jsonPath("$.sla.resolveBreached").value(true));
        mvc.perform(post(route + "/complete").with(as(worker, "lab-a", "agent")).contentType("application/json")
            .content("{\"expectedTaskKey\":\"handle\",\"outcome\":\"resolved\"}")).andExpect(status().isNoContent());
        var closed = slaService.read("lab-a", id);
        assertTrue(closed.project(closed.resolvedAt().plus(Duration.ofDays(60))).startBreached());
        assertTrue(closed.project(closed.resolvedAt().plus(Duration.ofDays(60))).resolveBreached());
        assertFalse(closed.project(closed.resolvedAt().plus(Duration.ofDays(60))).warning());
    }

    @Test void slaConcurrentStartsCommitOneTimestampAndOneAudit() throws Exception {
        String worker = "sla-concurrent-agent";
        var ticket = create("sla-concurrent-owner", "Concurrent SLA", "requester");
        var reviewed = body(review(ticket, "sla-coordinator", worker, null).andExpect(status().isOk()));
        String task = reviewed.path("taskId").asText();
        var pool = Executors.newFixedThreadPool(2);
        var ready = new CountDownLatch(2); var start = new CountDownLatch(1);
        try {
            java.util.concurrent.Callable<Integer> action = () -> {
                ready.countDown(); assertTrue(start.await(15, TimeUnit.SECONDS));
                return mvc.perform(post("/api/hpdi/v1/workspaces/lab-a/tasks/" + task + "/start")
                    .with(as(worker, "lab-a", "agent"))).andReturn().getResponse().getStatus();
            };
            var first = pool.submit(action); var second = pool.submit(action);
            assertTrue(ready.await(15, TimeUnit.SECONDS)); start.countDown();
            assertEquals(List.of(200, 409), List.of(first.get(30, TimeUnit.SECONDS), second.get(30, TimeUnit.SECONDS)).stream().sorted().toList());
            assertNotNull(slaService.read("lab-a", UUID.fromString(ticket.path("id").asText())).firstStarted());
            assertEquals(1, jdbc.queryForObject("select count(*) from audit_event where action='servicedesk.sla.start' and resource=?", Integer.class, ticket.path("id").asText()));
        } finally { start.countDown(); pool.shutdownNow(); }
    }

    @org.junit.jupiter.api.BeforeEach
    void availableRegressionAgents() {
        doReturn(true).when(duty).onDuty(anyString(), anyString());
        doAnswer(invocation ->
            directory.members(invocation.getArgument(0)).stream().map(vn.haui.dx.core.identity.api.DirectoryMember::sub)
                .collect(java.util.stream.Collectors.toSet())).when(directory).supportGroupMembers(anyString(), anyString());
    }

    private ResultActions review(JsonNode ticket, String reviewer, String assignee, Integer minutes) throws Exception {
        when(directory.members("lab-a")).thenReturn("unknown-agent".equals(assignee) ? List.of() : List.of(new vn.haui.dx.core.identity.api.DirectoryMember(
            Objects.toString(assignee,"review-agent"), Objects.toString(assignee,"review-agent"), null, java.util.Set.of(new vn.haui.dx.core.identity.api.WorkspaceRole("agent")))));
        var input = json.createObjectNode().put("category","HARDWARE").put("priority","HIGH").put("reason","Reviewed by coordinator");
        if (assignee == null) input.putNull("assignee"); else input.put("assignee",assignee);
        if (minutes != null) input.put("estimateMinutes",minutes);
        return mvc.perform(post(BASE+"/"+ticket.path("id").asText()+"/review").with(as(reviewer,"lab-a","coordinator"))
            .contentType("application/json").content(input.toString()));
    }

    @Test void dutyIsRecheckedAtReviewClaimAndAssignmentWithoutInterruptingActiveWork() throws Exception {
        doCallRealMethod().when(duty).onDuty(anyString(), anyString());
        String worker = "duty-worker-" + UUID.randomUUID();
        var ticket = create("duty-owner-" + UUID.randomUUID(), "Duty assignment", "requester");
        review(ticket, "duty-reviewer", worker, null).andExpect(status().isConflict());
        String dutyRoute = "/api/hpdi/v1/workspaces/lab-a/duty/me";
        mvc.perform(put(dutyRoute).with(as(worker,"lab-a","agent")).contentType("application/json").content("{\"onDuty\":true}"))
            .andExpect(status().isOk());
        String task = body(review(ticket, "duty-reviewer", worker, null).andExpect(status().isOk())).path("taskId").asText();
        String tasks = "/api/hpdi/v1/workspaces/lab-a/tasks/";
        mvc.perform(post(tasks+task+"/start").with(as(worker,"lab-a","agent"))).andExpect(status().isOk());
        mvc.perform(put(dutyRoute).with(as(worker,"lab-a","agent")).contentType("application/json").content("{\"onDuty\":false}"))
            .andExpect(status().isOk());
        mvc.perform(get("/api/hpdi/v1/workspaces/lab-a/queue").with(as(worker,"lab-a","agent"))).andExpect(status().isOk())
            .andExpect(jsonPath("$.items[0].workState").value("IN_PROGRESS"));
        mvc.perform(post(tasks+task+"/pause").with(as(worker,"lab-a","agent")).contentType("application/json").content("{\"reason\":\"WAITING_EXTERNAL\"}"))
            .andExpect(status().isOk());
        mvc.perform(post(tasks+task+"/assign").with(as("duty-reviewer","lab-a","coordinator")).contentType("application/json")
            .content(json.writeValueAsString(Map.of("assignee",worker)))).andExpect(status().isConflict());
        var unassigned = create("unassigned-owner-"+UUID.randomUUID(), "Off duty claim", "requester");
        String unassignedTask = body(review(unassigned,"duty-reviewer",null,null).andExpect(status().isOk())).path("taskId").asText();
        when(directory.members("lab-a")).thenReturn(List.of(new vn.haui.dx.core.identity.api.DirectoryMember(
            worker,worker,null,java.util.Set.of(vn.haui.dx.core.identity.api.WorkspaceRole.AGENT))));
        mvc.perform(post(tasks+unassignedTask+"/claim").with(as(worker,"lab-a","agent"))).andExpect(status().isConflict());
    }

    private String currentTask(JsonNode ticket, String owner) throws Exception {
        return body(mvc.perform(get(BASE+"/"+ticket.path("id").asText()+"/progress").with(as(owner,"lab-a","requester")))
            .andExpect(status().isOk())).path("currentTasks").get(0).path("id").asText();
    }

    @Test void coordinatorRoleMatrixDoesNotConflateHandlingPermissionWithAgentRole() throws Exception {
        String hpdi="/api/hpdi/v1/workspaces/lab-a/tasks/";
        for (var roles : List.of(new String[]{"coordinator"},new String[]{"coordinator","agent"},new String[]{"agent"},new String[]{"requester"})) {
            String actor="matrix-"+UUID.randomUUID();
            var ticket=create("matrix-owner-"+UUID.randomUUID(),"Role matrix","requester");
            when(directory.members("lab-a")).thenReturn(List.of(new vn.haui.dx.core.identity.api.DirectoryMember(actor,actor,null,
                Arrays.stream(roles).map(vn.haui.dx.core.identity.api.WorkspaceRole::new).collect(java.util.stream.Collectors.toSet()))));
            boolean coordinator=Arrays.asList(roles).contains("coordinator"), agent=Arrays.asList(roles).contains("agent");
            String input="{\"category\":\"ACCOUNT\",\"priority\":\"LOW\",\"assignee\":"+(agent ? "\""+actor+"\"" : "null")+",\"reason\":\"Matrix\"}";
            var response=mvc.perform(post(BASE+"/"+ticket.path("id").asText()+"/review").with(as(actor,"lab-a",roles))
                .contentType("application/json").content(input));
            if (!coordinator) { response.andExpect(status().isForbidden()); continue; }
            String task=body(response.andExpect(status().isOk())).path("taskId").asText();
            if (agent) {
                mvc.perform(post(hpdi+task+"/start").with(as(actor,"lab-a",roles))).andExpect(status().isOk());
                mvc.perform(post(hpdi+task+"/pause").with(as(actor,"lab-a",roles)).contentType("application/json")
                    .content("{\"reason\":\"WAITING_EXTERNAL\"}")).andExpect(status().isOk());
                for (String action : List.of("start","pause","resume","complete"))
                    mvc.perform(post(hpdi+task+"/"+action).with(as(actor,"lab-a","coordinator")).contentType("application/json")
                        .content("pause".equals(action)?"{\"reason\":\"WAITING_EXTERNAL\"}":"complete".equals(action)?"{\"expectedTaskKey\":\"handle\",\"outcome\":\"resolved\"}":"{}"))
                        .andExpect(status().isForbidden());
                mvc.perform(post(hpdi+task+"/resume").with(as(actor,"lab-a",roles))).andExpect(status().isOk());
            } else {
                mvc.perform(post(hpdi+task+"/claim").with(as(actor,"lab-a",roles))).andExpect(status().isForbidden());
                for (String action : List.of("start","pause","resume","complete"))
                    mvc.perform(post(hpdi+task+"/"+action).with(as(actor,"lab-a",roles)).contentType("application/json")
                        .content("pause".equals(action)?"{\"reason\":\"WAITING_EXTERNAL\"}":"complete".equals(action)?"{\"expectedTaskKey\":\"handle\",\"outcome\":\"resolved\"}":"{}"))
                        .andExpect(status().isForbidden());
            }
        }
    }

    @Test void nullableAssigneeCanBeClaimedOrAssignedWithoutEnteringAnAgentQueueEarly() throws Exception {
        String worker="null-worker-"+UUID.randomUUID(), owner="null-owner-"+UUID.randomUUID();
        String hpdi="/api/hpdi/v1/workspaces/lab-a";
        for (String action : List.of("claim","assign")) {
            var ticket=create(owner,"Unassigned "+action,"requester");
            String task=body(review(ticket,"null-coordinator",null,null).andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("ASSIGNED")).andExpect(jsonPath("$.assignee").isEmpty())).path("taskId").asText();
            mvc.perform(get(hpdi+"/queue").with(as(worker,"lab-a","agent"))).andExpect(status().isOk()).andExpect(jsonPath("$.total").value(0));
            mvc.perform(post(hpdi+"/tasks/"+task+"/claim").with(as(owner,"lab-a","requester","agent"))).andExpect(status().isForbidden());
            when(directory.members("lab-a")).thenReturn(List.of(new vn.haui.dx.core.identity.api.DirectoryMember(worker,worker,null,
                java.util.Set.of(new vn.haui.dx.core.identity.api.WorkspaceRole("agent")))));
            mvc.perform(post(hpdi+"/tasks/"+task+"/"+action).with(as("claim".equals(action)?worker:"null-coordinator","lab-a","claim".equals(action)?"agent":"coordinator"))
                .contentType("application/json").content("{\"assignee\":\""+worker+"\"}")).andExpect(status().isOk());
            mvc.perform(get(hpdi+"/queue").with(as(worker,"lab-a","agent"))).andExpect(status().isOk()).andExpect(jsonPath("$.items[0].id").value(task));
            mvc.perform(post(hpdi+"/tasks/"+task+"/start").with(as(worker,"lab-a","agent"))).andExpect(status().isOk());
            mvc.perform(post(hpdi+"/tasks/"+task+"/complete").with(as(worker,"lab-a","agent")).contentType("application/json")
                .content("{\"expectedTaskKey\":\"handle\",\"outcome\":\"resolved\"}")).andExpect(status().isNoContent());
        }
    }

    @Test void createProjectionFailureRollsBackTicketWorkflowAndAudit() throws Exception {
        long tickets=jdbc.queryForObject("select count(*) from servicedesk_ticket",Long.class);
        long processes=jdbc.queryForObject("select count(*) from process_scope",Long.class);
        long audits=jdbc.queryForObject("select count(*) from audit_event",Long.class);
        doThrow(vn.haui.dx.hpdi.common.api.CapabilityException.conflict("Projection failure")).when(reviewViews).view(eq("lab-a"),any(Ticket.class));
        try {
            submit("projection-owner",UUID.randomUUID(),"Projection failure","requester").andExpect(status().isConflict());
            assertEquals(tickets,jdbc.queryForObject("select count(*) from servicedesk_ticket",Long.class));
            assertEquals(processes,jdbc.queryForObject("select count(*) from process_scope",Long.class));
            assertEquals(audits,jdbc.queryForObject("select count(*) from audit_event",Long.class));
        } finally { reset(reviewViews); }
    }

    @Test void queueProjectionQueriesScaleByBatchNotByTaskOrReturnedPage() throws Exception {
        String worker="bulk-worker-"+UUID.randomUUID();
        for (int index=0; index<101; index++)
            review(create("bulk-owner","Bulk "+index,"requester"),"bulk-coordinator",worker,null).andExpect(status().isOk());
        clearInvocations(processViews,reviewViews,jdbc);
        mvc.perform(get("/api/hpdi/v1/workspaces/lab-a/queue").param("page","1").param("size","100").with(as(worker,"lab-a","agent")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(101)).andExpect(jsonPath("$.items.length()").value(1))
            .andExpect(jsonPath("$.loadMinutes").value(9090));
        verify(processViews,never()).timeline(anyString(),anyString());
        verify(reviewViews,never()).project(anyString(),any());
        verify(reviewViews,times(2)).projectBatch(eq("lab-a"),anyList());
        verify(jdbc,times(2)).query(startsWith("select * from servicedesk_sla where workspace=? and handle_task_id in"),
            any(org.springframework.jdbc.core.RowMapper.class),any(Object[].class));
        verify(jdbc,times(2)).queryForList(startsWith("select t.process_instance_id,t.title"),any(Object[].class));
        verify(jdbc,times(3)).queryForObject(startsWith("select count(*) from act_ru_task"),eq(Long.class),any(Object[].class));
        verify(jdbc,times(3)).query(startsWith("select t.id_, t.proc_inst_id_"),any(org.springframework.jdbc.core.RowMapper.class),any(Object[].class));
    }

    @Test void repeatedReworkRetainsOneProcessFreshReviewsElapsedAndAudit() throws Exception {
        String owner="round-owner-"+UUID.randomUUID(), worker="round-worker-"+UUID.randomUUID();
        var ticket=create(owner,"Repeated rework","requester");
        String route=BASE+"/"+ticket.path("id").asText(), hpdi="/api/hpdi/v1/workspaces/lab-a/tasks/";
        String process=ticket.path("process").path("processInstanceId").asText();
        for (int round=0; round<3; round++) {
            var reviewed=body(review(ticket,"round-coordinator",worker,round==1?120:null).andExpect(status().isOk()));
            assertEquals(process,reviewed.path("process").path("processInstanceId").asText());
            assertEquals(round,reviewed.path("reopenCount").asInt());
            assertEquals(round,reviewed.path("workedMinutes").asInt());
            assertEquals(round==0?"ASSIGNED":"REOPENED",reviewed.path("state").asText());
            String task=reviewed.path("taskId").asText();
            mvc.perform(post(hpdi+task+"/start").with(as(worker,"lab-a","agent"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.reopenCount").value(round));
            engineState.elapse(task,60_000);
            mvc.perform(post(hpdi+task+"/complete").with(as(worker,"lab-a","agent")).contentType("application/json")
                .content("{\"expectedTaskKey\":\"handle\",\"outcome\":\"resolved\"}")).andExpect(status().isNoContent());
            metrics(statistics("lab-a",owner,"Repeated rework","requester"),0,1,0,0);
            String confirm=currentTask(ticket,owner);
            mvc.perform(post(hpdi+confirm+"/complete").with(as(owner,"lab-a","requester")).contentType("application/json")
                .content("{\"expectedTaskKey\":\"confirm\",\"outcome\":\""+(round<2?"rework":"accepted")+"\"}")).andExpect(status().isNoContent());
            mvc.perform(get(route).with(as(owner,"lab-a","requester"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value(round<2?"REOPENED":"CLOSED"))
                .andExpect(jsonPath("$.workedMinutes").value(round+1));
            metrics(statistics("lab-a",owner,"Repeated rework","requester"),round<2?1:0,0,round<2?0:1,0);
            mvc.perform(get("/api/hpdi/v1/workspaces/lab-a/queue").with(as(worker,"lab-a","agent")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(0));
        }
        assertEquals(1,jdbc.queryForObject("select count(*) from servicedesk_ticket where process_instance_id=?",Integer.class,process));
        assertEquals(1,jdbc.queryForObject("select count(*) from process_scope where id=?",Integer.class,process));
        assertEquals(4,jdbc.queryForObject("select count(*) from servicedesk_estimate_history where ticket_id=?",Integer.class,UUID.fromString(ticket.path("id").asText())));
        assertEquals(9,jdbc.queryForObject("select count(*) from audit_event where action='task.complete' and details->>'processInstanceId'=?",Integer.class,process));
    }

    @Test void reviewMigrationsValidateOnPostgresWithIndexedRoundHistory() {
        assertTrue(jdbc.queryForObject("select version()",String.class).startsWith("PostgreSQL 16.6"));
        assertEquals(2,jdbc.queryForObject("select count(*) from flyway_schema_history where version in ('8','9') and success",Integer.class));
        assertEquals(1, jdbc.queryForObject("select count(*) from pg_indexes where schemaname=current_schema() "
                + "and tablename='servicedesk_estimate_history' and indexname='servicedesk_estimate_history_rounds'", Integer.class));
    }

    @Test void reviewedHttpLifecycleHasDefaultEstimatePendingLoadAuditAndRequesterConfirmation() throws Exception {
        String worker="lifecycle-agent-"+UUID.randomUUID();
        String owner="review-owner-"+UUID.randomUUID();
        var ticket=create(owner,"Reviewed lifecycle","requester","agent");
        String ticketRoute=BASE+"/"+ticket.path("id").asText();
        mvc.perform(get(ticketRoute).with(as(owner,"lab-a","requester"))).andExpect(status().isOk())
            .andExpect(jsonPath("$.state").value("AWAITING_REVIEW"));
        String hpdi="/api/hpdi/v1/workspaces/lab-a";
        mvc.perform(get(hpdi+"/queue").with(as(worker,"lab-a","agent"))).andExpect(status().isOk())
            .andExpect(jsonPath("$.items").isEmpty());
        var reviewed=body(review(ticket,"review-coordinator",worker,null).andExpect(status().isOk())
            .andExpect(jsonPath("$.state").value("ASSIGNED")).andExpect(jsonPath("$.estimate.minutes").value(90))
            .andExpect(jsonPath("$.estimate.basis").value("DEFAULT")));
        String task=reviewed.path("taskId").asText();
        mvc.perform(post(hpdi+"/tasks/"+task+"/start")).andExpect(status().isUnauthorized());
        mvc.perform(post(hpdi+"/tasks/"+task+"/start").with(as(owner,"lab-a","agent"))).andExpect(status().isForbidden());
        mvc.perform(post(hpdi+"/tasks/"+task+"/start").with(as("review-coordinator","lab-a","coordinator"))).andExpect(status().isForbidden());
        mvc.perform(post(hpdi.replace("lab-a","lab-b")+"/tasks/"+task+"/start").with(as(worker,"lab-b","agent")))
            .andExpect(status().isNotFound());
        mvc.perform(post(hpdi+"/tasks/"+task+"/start").with(as(worker,"lab-a","agent")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(task)).andExpect(jsonPath("$.label").value("Reviewed lifecycle"))
            .andExpect(jsonPath("$.workState").value("IN_PROGRESS")).andExpect(jsonPath("$.priority").value("HIGH"))
            .andExpect(jsonPath("$.remainingMinutes").value(90));
        mvc.perform(post(hpdi+"/tasks/"+task+"/pause").with(as(worker,"lab-a","agent"))
            .contentType("application/json").content("{}" )).andExpect(status().isBadRequest());
        mvc.perform(post(hpdi+"/tasks/"+task+"/pause").with(as(worker,"lab-a","agent"))
            .contentType("application/json").content("{\"reason\":\"WAITING_PARTS\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.workState").value("PENDING")).andExpect(jsonPath("$.remainingMinutes").value(45));
        mvc.perform(get(hpdi+"/queue").with(as(worker,"lab-a","agent")).param("size","1"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.loadMinutes").value(45)).andExpect(jsonPath("$.total").value(1));
        mvc.perform(post(hpdi+"/tasks/"+task+"/resume").with(as(worker,"lab-a","agent")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.workState").value("IN_PROGRESS"));
        mvc.perform(post(hpdi+"/tasks/"+task+"/complete").with(as(worker,"lab-a","agent"))
            .contentType("application/json").content("{\"expectedTaskKey\":\"handle\",\"outcome\":\"resolved\"}"))
            .andExpect(status().isNoContent());
        String confirm=currentTask(ticket,owner);
        mvc.perform(post(hpdi+"/tasks/"+confirm+"/start").with(as(owner,"lab-a","agent"))).andExpect(status().isForbidden());
        mvc.perform(post(hpdi+"/tasks/"+confirm+"/complete").with(as(owner,"lab-a","requester"))
            .contentType("application/json").content("{\"expectedTaskKey\":\"confirm\",\"outcome\":\"accepted\"}"))
            .andExpect(status().isNoContent());
        mvc.perform(get(ticketRoute).with(as(owner,"lab-a","requester"))).andExpect(status().isOk()).andExpect(jsonPath("$.state").value("CLOSED"));
        assertEquals(2,jdbc.queryForObject("select count(*) from audit_event where action='task.work-interval' and resource=?",Integer.class,task));
    }

    @Test void reviewCannotBypassBusinessGuardOrAssignOwnerOrCoordinatorWithoutAgentAndRollsBack() throws Exception {
        String owner="guard-owner-"+UUID.randomUUID();
        var ticket=create(owner,"Review guard","requester","agent");
        String task=currentTask(ticket,owner);
        String hpdi="/api/hpdi/v1/workspaces/lab-a/tasks/"+task;
        mvc.perform(post(hpdi+"/claim").with(as("review-coordinator","lab-a","coordinator"))).andExpect(status().isOk());
        mvc.perform(post(hpdi+"/complete").with(as("review-coordinator","lab-a","coordinator"))
            .contentType("application/json").content("{\"expectedTaskKey\":\"review\",\"outcome\":\"approved\"}"))
            .andExpect(status().isConflict());
        review(ticket,"review-coordinator",owner,null).andExpect(status().isForbidden());
        review(ticket,"review-coordinator","unknown-agent",null).andExpect(status().isBadRequest());
        assertEquals(0,jdbc.queryForObject("select count(*) from servicedesk_review where ticket_id=?",Integer.class,UUID.fromString(ticket.path("id").asText())));
        assertEquals(0,jdbc.queryForObject("select count(*) from servicedesk_estimate_history where ticket_id=?",Integer.class,UUID.fromString(ticket.path("id").asText())));
        assertEquals(task,currentTask(ticket,owner));
        review(ticket,"review-coordinator","review-agent",120).andExpect(status().isOk()).andExpect(jsonPath("$.estimate.basis").value("COORDINATOR"));
        review(ticket,"review-coordinator","review-agent",90).andExpect(status().isConflict());
    }

    @Test void concurrentCoordinatorReviewsHaveOneWinnerAndOneEstimateHistory() throws Exception {
        var ticket=create("concurrent-review-owner","Concurrent review","requester");
        var ready=new CountDownLatch(2); var start=new CountDownLatch(1); var pool=Executors.newFixedThreadPool(2);
        try {
            java.util.concurrent.Callable<Integer> action=() -> { ready.countDown(); assertTrue(start.await(15,TimeUnit.SECONDS));
                return review(ticket,"coordinator-"+Thread.currentThread().getId(),null,null).andReturn().getResponse().getStatus(); };
            var first=pool.submit(action); var second=pool.submit(action);
            assertTrue(ready.await(15,TimeUnit.SECONDS)); start.countDown();
            assertEquals(List.of(200,409),List.of(first.get(30,TimeUnit.SECONDS),second.get(30,TimeUnit.SECONDS)).stream().sorted().toList());
            assertEquals(1,jdbc.queryForObject("select count(*) from servicedesk_estimate_history where ticket_id=?",Integer.class,UUID.fromString(ticket.path("id").asText())));
            assertEquals(1,jdbc.queryForObject("select count(*) from servicedesk_sla where ticket_id=?",Integer.class,UUID.fromString(ticket.path("id").asText())));
        } finally { start.countDown(); pool.shutdownNow(); }
    }

    @Test void reviewAuditFailureRollsBackReviewAssignmentEngineAndEstimateHistory() throws Exception {
        var ticket=create("review-rollback-owner","Review rollback","requester");
        String task=currentTask(ticket,"review-rollback-owner");
        jdbc.execute("create function review_reject_audit() returns trigger language plpgsql as $$ begin if new.action='servicedesk.ticket.review' then raise exception 'review rejection' using errcode='23514'; end if; return new; end $$");
        jdbc.execute("create trigger review_reject before insert on audit_event for each row execute function review_reject_audit()");
        try {
            review(ticket,"review-coordinator","review-agent",120).andExpect(status().isConflict());
            assertEquals(task,currentTask(ticket,"review-rollback-owner"));
            assertEquals(0,jdbc.queryForObject("select count(*) from servicedesk_review where ticket_id=?",Integer.class,UUID.fromString(ticket.path("id").asText())));
            assertEquals(0,jdbc.queryForObject("select count(*) from servicedesk_estimate_history where ticket_id=?",Integer.class,UUID.fromString(ticket.path("id").asText())));
            assertEquals(0,jdbc.queryForObject("select count(*) from servicedesk_sla where ticket_id=?",Integer.class,UUID.fromString(ticket.path("id").asText())));
        } finally { jdbc.execute("drop trigger review_reject on audit_event"); jdbc.execute("drop function review_reject_audit()"); }
    }


    @Test void publicQueuePaginationSortingAndReworkKeepActualElapsedAndContractEvidence() throws Exception {
        String worker="queue-agent-"+UUID.randomUUID(), owner="queue-owner-"+UUID.randomUUID();
        var first=create(owner,"Queue first","requester");
        var second=create(owner,"Queue second","requester");
        String hpdi="/api/hpdi/v1/workspaces/lab-a";
        var firstReview=body(review(first,"review-coordinator",worker,30).andExpect(status().isOk()));
        var reviewed=body(review(second,"review-coordinator",worker,120).andExpect(status().isOk()));
        String task=reviewed.path("taskId").asText();
        var started=body(mvc.perform(post(hpdi+"/tasks/"+task+"/start").with(as(worker,"lab-a","agent"))).andExpect(status().isOk()));
        engineState.elapse(task,60_000);
        clearInvocations(processViews);
        var queued=body(mvc.perform(get(hpdi+"/queue").with(as(worker,"lab-a","agent")).param("size","1"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].id").value(task))
            .andExpect(jsonPath("$.total").value(2)).andExpect(jsonPath("$.loadMinutes").value(149)));
        verify(processViews,never()).timeline(anyString(),anyString());
        mvc.perform(get(hpdi+"/queue").with(as(worker,"lab-a","agent")).param("page","1").param("size","1"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].id").value(firstReview.path("taskId").asText()));
        mvc.perform(get(hpdi+"/queue").with(as(worker,"lab-a","agent")).param("page","2").param("size","1"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.items").isEmpty()).andExpect(jsonPath("$.loadMinutes").value(149));
        var paused=body(mvc.perform(post(hpdi+"/tasks/"+task+"/pause").with(as(worker,"lab-a","agent"))
            .contentType("application/json").content("{\"reason\":\"WAITING_EXTERNAL\"}" )).andExpect(status().isOk()));
        var resumed=body(mvc.perform(post(hpdi+"/tasks/"+task+"/resume").with(as(worker,"lab-a","agent"))).andExpect(status().isOk()));
        mvc.perform(post(hpdi+"/tasks/"+task+"/complete").with(as(worker,"lab-a","agent"))
            .contentType("application/json").content("{\"expectedTaskKey\":\"handle\",\"outcome\":\"resolved\"}" )).andExpect(status().isNoContent());
        String confirm=currentTask(second,owner);
        mvc.perform(post(hpdi+"/tasks/"+confirm+"/complete").with(as(owner,"lab-a","requester"))
            .contentType("application/json").content("{\"expectedTaskKey\":\"confirm\",\"outcome\":\"rework\"}" )).andExpect(status().isNoContent());
        String reviewTask=currentTask(second,owner);
        mvc.perform(get(BASE+"/"+second.path("id").asText()).with(as(owner,"lab-a","requester")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.state").value("REOPENED"));
        mvc.perform(post(hpdi+"/tasks/"+reviewTask+"/claim").with(as("review-coordinator","lab-a","coordinator"))).andExpect(status().isOk());
        mvc.perform(post(hpdi+"/tasks/"+reviewTask+"/complete").with(as("review-coordinator","lab-a","coordinator"))
            .contentType("application/json").content("{\"expectedTaskKey\":\"review\",\"outcome\":\"approved\"}" )).andExpect(status().isConflict());
        mvc.perform(get(BASE).param("state","AWAITING_REVIEW").param("q","Queue second").param("sort","priority")
            .with(as("review-coordinator","lab-a","coordinator"))).andExpect(status().isOk()).andExpect(jsonPath("$.total").value(1));
        mvc.perform(get(BASE).param("state","AWAITING_REVIEW").with(as(worker,"lab-a","agent"))).andExpect(status().isForbidden());
        review(second,"review-coordinator",worker,null).andExpect(status().isOk()).andExpect(jsonPath("$.reopenCount").value(1))
            .andExpect(jsonPath("$.workedMinutes").value(1));
        var evidence=Map.of("review",reviewed,"start",started,"queue",queued,"pause",paused,"resume",resumed,"created",first);
        java.nio.file.Files.writeString(Path.of("target/servicedesk-http-evidence.json"),json.writeValueAsString(evidence));
        assertEquals(3,jdbc.queryForObject("select count(*) from servicedesk_estimate_history where ticket_id=?",Integer.class,
            UUID.fromString(second.path("id").asText())));
    }

    @Test void completionAuditFailureRollsBackApplicationElapsedTogetherWithFlowable() throws Exception {
        String owner="elapsed-owner-"+UUID.randomUUID(),worker="elapsed-agent-"+UUID.randomUUID();
        var ticket=create(owner,"Elapsed rollback","requester");
        var reviewed=body(review(ticket,"review-coordinator",worker,null).andExpect(status().isOk()));
        String task=reviewed.path("taskId").asText(), hpdi="/api/hpdi/v1/workspaces/lab-a/tasks/"+task;
        mvc.perform(post(hpdi+"/start").with(as(worker,"lab-a","agent"))).andExpect(status().isOk());
        engineState.elapse(task,120_000);
        jdbc.execute("create function execution_reject_audit() returns trigger language plpgsql as $$ begin if new.action='task.complete' then raise exception 'completion rejection' using errcode='23514'; end if; return new; end $$");
        jdbc.execute("create trigger execution_reject before insert on audit_event for each row execute function execution_reject_audit()");
        try {
            mvc.perform(post(hpdi+"/complete").with(as(worker,"lab-a","agent")).contentType("application/json")
                .content("{\"expectedTaskKey\":\"handle\",\"outcome\":\"resolved\"}" )).andExpect(status().isConflict());
            assertEquals(0L,jdbc.queryForObject("select worked_millis from servicedesk_ticket where id=?",Long.class,UUID.fromString(ticket.path("id").asText())));
            assertEquals(task,currentTask(ticket,owner));
            assertEquals(0,jdbc.queryForObject("select count(*) from audit_event where action='task.work-interval' and resource=?",Integer.class,task));
            mvc.perform(get(BASE+"/"+ticket.path("id").asText()).with(as(owner,"lab-a","requester")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.workState").value("IN_PROGRESS"));
        } finally { jdbc.execute("drop trigger execution_reject on audit_event");jdbc.execute("drop function execution_reject_audit()"); }
        mvc.perform(post(hpdi+"/complete").with(as(worker,"lab-a","agent")).contentType("application/json")
            .content("{\"expectedTaskKey\":\"handle\",\"outcome\":\"resolved\"}" )).andExpect(status().isNoContent());
        mvc.perform(get(BASE+"/"+ticket.path("id").asText()).with(as(owner,"lab-a","requester")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.workedMinutes").value(2)).andExpect(jsonPath("$.state").value("AWAITING_CONFIRMATION"));
    }

    @Test void reviewRoleWorkspaceBodyAndTargetAgentRoleAreValidated() throws Exception {
        var ticket=create("role-owner","Review role validation","requester");
        String route=BASE+"/"+ticket.path("id").asText()+"/review";
        String input="{\"category\":\"ACCOUNT\",\"priority\":\"LOW\",\"assignee\":\"coordinator-only\",\"reason\":\"Review\"}";
        mvc.perform(post(route).contentType("application/json").content(input)).andExpect(status().isUnauthorized());
        mvc.perform(post(route).with(as("agent","lab-a","agent")).contentType("application/json").content(input)).andExpect(status().isForbidden());
        mvc.perform(post(route).with(as("foreign","lab-b","coordinator")).contentType("application/json").content(input)).andExpect(status().isForbidden());
        mvc.perform(post(route).with(as("reviewer","lab-a","coordinator")).contentType("application/json").content("{}" )).andExpect(status().isBadRequest());
        when(directory.members("lab-a")).thenReturn(List.of(new vn.haui.dx.core.identity.api.DirectoryMember("coordinator-only","coordinator-only",null,
            java.util.Set.of(new vn.haui.dx.core.identity.api.WorkspaceRole("coordinator")))));
        mvc.perform(post(route).with(as("reviewer","lab-a","coordinator")).contentType("application/json").content(input)).andExpect(status().isBadRequest());
        assertEquals(0,jdbc.queryForObject("select count(*) from servicedesk_review where ticket_id=?",Integer.class,UUID.fromString(ticket.path("id").asText())));
        mvc.perform(post(route).with(as("reviewer","lab-a","coordinator")).contentType("application/json")
            .content(input.replace("\"assignee\":\"coordinator-only\",", ""))).andExpect(status().isBadRequest());
    }

    @Test void reviewQueueFilteringAndProjectionShareSnapshotDuringConcurrentReview() throws Exception {
        String owner="snapshot-owner-"+UUID.randomUUID(), title="snapshot-review-"+UUID.randomUUID();
        var ticket=create(owner,title,"requester");
        UUID id=UUID.fromString(ticket.path("id").asText());
        var triggered=new java.util.concurrent.atomic.AtomicBoolean();
        var writer=Executors.newSingleThreadExecutor();
        try {
            doAnswer(invocation -> {
                Ticket selected=invocation.getArgument(1);
                if(id.equals(selected.id()) && triggered.compareAndSet(false,true))
                    writer.submit(() -> { review(ticket,"snapshot-coordinator",null,null).andExpect(status().isOk());return null; }).get(30,TimeUnit.SECONDS);
                return invocation.callRealMethod();
            }).when(reviewViews).view(eq("lab-a"),any(Ticket.class));
            mvc.perform(get(BASE).param("state","AWAITING_REVIEW").param("q",title).with(as("snapshot-coordinator","lab-a","coordinator")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(1)).andExpect(jsonPath("$.items[0].state").value("AWAITING_REVIEW"));
            assertTrue(triggered.get());
            mvc.perform(get(BASE+"/"+id).with(as(owner,"lab-a","requester"))).andExpect(status().isOk()).andExpect(jsonPath("$.state").value("ASSIGNED"));
        } finally { writer.shutdownNow(); }
    }

    @Test void defaultEstimateTableMatchesAllTwentyBusinessDefaults() {
        int[][] expected={{45,60,90,120},{30,45,60,90},{30,30,45,60},{15,15,20,30},{30,45,60,90}};
        for(var category:TicketReview.Category.values()) for(var priority:TicketReview.Priority.values())
            assertEquals(expected[category.ordinal()][priority.ordinal()],TicketReview.defaultMinutes(category,priority));
    }

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
        assertEquals(ServiceDeskProcess.REVIEWED_KEY, ticket.path("process").path("processKey").asText());
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
            .when(workflow).start(eq("lab-a"), eq(ServiceDeskProcess.REVIEWED_KEY), anyString(), anyMap());
        submit("start-failure", UUID.randomUUID(), "Failure", "requester").andExpect(status().isConflict());
        assertArrayEquals(before, counts());
    }

    @Test void failureAfterRealWorkflowStartAlsoRollsBack() throws Exception {
        long[] before = counts();
        doAnswer(invocation -> {
            invocation.callRealMethod();
            throw CoreException.conflict("Failure after start");
        }).when(workflow).start(eq("lab-a"), eq(ServiceDeskProcess.REVIEWED_KEY), anyString(), anyMap());
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
        var context=SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new JwtAuthenticationToken(Jwt.withTokenValue("legacy-fixture").header("alg","none")
            .subject("workflow-owner").claim("groups",List.of("/workspaces/lab-a/requester","/workspaces/lab-a/agent")).build()));
        var previous=SecurityContextHolder.getContext();
        UUID id=UUID.randomUUID();
        try {
            SecurityContextHolder.setContext(context);
            var process=workflow.start("lab-a",ServiceDeskProcess.KEY,id.toString(),Map.of());
            jdbc.update("insert into servicedesk_ticket(id,workspace,creator,client_request_id,title,description,process_instance_id) values(?,?,?,?,?,?,?)",
                id,"lab-a","workflow-owner",UUID.randomUUID(),"Workflow","Legacy fixture",process.id());
        } finally { SecurityContextHolder.setContext(previous); }
        var ticket=body(mvc.perform(get(BASE+"/"+id).with(as("workflow-owner","lab-a","requester"))).andExpect(status().isOk()));
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
        assertEquals(4L, jdbc.queryForObject("select count(*) from audit_event where action='task.complete' "
            + "and details->>'processInstanceId'=?", Long.class, ticket.path("process").path("processInstanceId").asText()));
    }
}
