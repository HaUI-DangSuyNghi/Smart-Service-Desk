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
@SpringBootTest(properties = "serviceDesk.confirmation.scheduled=false")
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
    @SpyBean vn.haui.dx.hpdi.process.api.ProcessObservations statisticsObservations;
    @Autowired vn.haui.dx.core.identity.api.WorkspaceDirectory directory;
    @SpyBean vn.haui.dx.hpdi.human.api.AgentDuty duty;
    @Autowired TicketSlaService slaService;
    @Autowired TicketAlerts ticketAlerts;
    @Autowired vn.haui.dx.hpdi.process.api.ProcessTimers confirmationTimers;
    @Autowired TicketConfirmation confirmation;
    @SpyBean vn.haui.dx.core.identity.api.ServiceIdentity timerIdentity;
    @SpyBean java.time.Clock confirmationClock;

    private JsonNode awaitingConfirmation(String owner) throws Exception {
        String worker = "r11-agent-" + UUID.randomUUID();
        var ticket = create(owner, "R11 confirmation", "requester");
        String task = body(review(ticket, "coordinator", worker, null).andExpect(status().isOk())).path("taskId").asText();
        String route = "/api/hpdi/v1/workspaces/lab-a/tasks/" + task;
        mvc.perform(post(route + "/start").with(as(worker,"lab-a","agent"))).andExpect(status().isOk());
        mvc.perform(post(route + "/complete").with(as(worker,"lab-a","agent")).contentType("application/json")
            .content("{\"expectedTaskKey\":\"handle\",\"outcome\":\"resolved\"}")).andExpect(status().isNoContent());
        return body(mvc.perform(get(BASE + "/" + ticket.path("id").asText()).with(as(owner,"lab-a","requester"))).andExpect(status().isOk()));
    }

    private void authorizedTimerFixture(java.time.Instant at) {
        doReturn(at).when(confirmationClock).instant();
        doAnswer(invocation -> {
            var previous = SecurityContextHolder.getContext();
            var context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(new JwtAuthenticationToken(Jwt.withTokenValue("timer-test").header("alg","none")
                .subject("timer-service").claim("groups",List.of("/workspaces/lab-a/workflow-timer")).build()));
            try { SecurityContextHolder.setContext(context); ((Runnable) invocation.getArgument(2)).run(); }
            finally { SecurityContextHolder.setContext(previous); }
            return null;
        }).when(timerIdentity).run(eq("lab-a"),eq("workflow:timer"),any());
    }

    private void evaluate(JsonNode ticket) {
        confirmationTimers.evaluate("lab-a", ticket.path("process").path("processInstanceId").asText(), ticket.path("confirmGeneration").asText());
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans={false,true})
    void r11LegacyEnrollmentIsAtomicIdempotentAndHoldsOverdue(boolean overdue) throws Exception {
        var ticket = awaitingConfirmation("r11-legacy-" + UUID.randomUUID());
        String generation = ticket.path("confirmGeneration").asText();
        var before = jdbc.queryForList("select * from servicedesk_ticket where id=?::uuid", ticket.path("id").asText());
        var start = java.time.Instant.parse(ticket.path("confirmStartedAt").asText());
        var deadline = start.plus(Duration.ofHours(72));
        jdbc.update("delete from servicedesk_confirmation where generation=?",generation);
        authorizedTimerFixture(overdue ? deadline : start.plusSeconds(1));
        var executor = Executors.newFixedThreadPool(2);
        try {
            var first = executor.submit(() -> confirmationTimers.enrollConfirmations());
            var second = executor.submit(() -> confirmationTimers.enrollConfirmations());
            first.get(30,TimeUnit.SECONDS); second.get(30,TimeUnit.SECONDS);
        } finally { executor.shutdownNow(); }
        confirmationTimers.enrollConfirmations();
        var state = confirmation.read("lab-a",UUID.fromString(ticket.path("id").asText()));
        assertNotNull(state);
        assertEquals(start,state.confirmStartedAt());
        assertEquals(deadline,state.confirmDeadlineAt());
        assertEquals(overdue ? Boolean.TRUE : null,state.confirmationHistory().get(0).rolloutHold());
        assertEquals(before,jdbc.queryForList("select * from servicedesk_ticket where id=?::uuid",ticket.path("id").asText()));
        assertEquals(1,jdbc.queryForObject("select count(*) from audit_event where action='servicedesk.confirmation.enrolled' and resource=?",Integer.class,ticket.path("id").asText()));
        authorizedTimerFixture(deadline);
        evaluate(ticket);
        assertEquals(overdue ? null : "AUTO",confirmation.read("lab-a",UUID.fromString(ticket.path("id").asText())).closedBy());
    }

    @Test void r11LegacyEnrollmentAuditFailureRollsBackAndRetries() throws Exception {
        var ticket = awaitingConfirmation("r11-enroll-rollback-" + UUID.randomUUID());
        String generation = ticket.path("confirmGeneration").asText();
        jdbc.update("delete from servicedesk_confirmation where generation=?",generation);
        authorizedTimerFixture(java.time.Instant.parse(ticket.path("confirmStartedAt").asText()).plusSeconds(1));
        jdbc.execute("create function r11_enroll_reject() returns trigger language plpgsql as $$ begin "
            + "if new.action='servicedesk.confirmation.enrolled' then raise exception 'test rejection'; end if; return new; end $$");
        jdbc.execute("create trigger r11_enroll_reject before insert on audit_event for each row execute function r11_enroll_reject()");
        try {
            confirmationTimers.enrollConfirmations();
            assertNull(confirmation.read("lab-a",UUID.fromString(ticket.path("id").asText())));
        } finally {
            jdbc.execute("drop trigger r11_enroll_reject on audit_event"); jdbc.execute("drop function r11_enroll_reject()");
        }
        confirmationTimers.enrollConfirmations();
        assertNotNull(confirmation.read("lab-a",UUID.fromString(ticket.path("id").asText())));
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings={"accepted","rework"})
    void r11LegacyEnrollmentRacesRequesterWithoutDuplicateGeneration(String outcome) throws Exception {
        String owner = "r11-enroll-race-" + UUID.randomUUID();
        var ticket = awaitingConfirmation(owner);
        String generation = ticket.path("confirmGeneration").asText();
        jdbc.update("delete from servicedesk_confirmation where generation=?",generation);
        authorizedTimerFixture(java.time.Instant.parse(ticket.path("confirmStartedAt").asText()).plusSeconds(1));
        var start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var enrollment = executor.submit(() -> { start.await(); confirmationTimers.enrollConfirmations(); return null; });
            var requester = executor.submit(() -> {
                start.await();
                return mvc.perform(post("/api/hpdi/v1/workspaces/lab-a/tasks/"+generation+"/complete")
                    .with(as(owner,"lab-a","requester")).contentType("application/json")
                    .content("{\"expectedTaskKey\":\"confirm\",\"outcome\":\""+outcome+"\"}"))
                    .andReturn().getResponse().getStatus();
            });
            start.countDown(); enrollment.get(30,TimeUnit.SECONDS);
            assertEquals(204,requester.get(30,TimeUnit.SECONDS));
        } finally { executor.shutdownNow(); }
        confirmationTimers.enrollConfirmations();
        var history = confirmation.read("lab-a",UUID.fromString(ticket.path("id").asText())).confirmationHistory();
        assertEquals(1,history.size());
        assertEquals("accepted".equals(outcome) ? "REQUESTER" : "REWORK",history.get(0).decision());
    }

    @Test void r11EnrollmentRejectsStaleTicketAndScopeCorrelations() throws Exception {
        var ticket = awaitingConfirmation("r11-stale-"+UUID.randomUUID());
        String generation = ticket.path("confirmGeneration").asText();
        String process = ticket.path("process").path("processInstanceId").asText();
        jdbc.update("delete from servicedesk_confirmation where generation=?",generation);
        authorizedTimerFixture(java.time.Instant.parse(ticket.path("confirmStartedAt").asText()).plusSeconds(1));
        jdbc.update("update process_scope set owner='stale-owner' where id=?",process);
        confirmationTimers.enrollConfirmations();
        assertNull(confirmation.read("lab-a",UUID.fromString(ticket.path("id").asText())));
        jdbc.update("update process_scope set owner=? where id=?",ticket.path("creator").asText(),process);
        jdbc.update("update servicedesk_ticket set process_key=? where id=?::uuid",ServiceDeskProcess.KEY,ticket.path("id").asText());
        confirmationTimers.enrollConfirmations();
        assertNull(confirmation.read("lab-a",UUID.fromString(ticket.path("id").asText())));
        jdbc.update("update servicedesk_ticket set process_key=? where id=?::uuid",ServiceDeskProcess.REVIEWED_KEY,ticket.path("id").asText());
        confirmationTimers.enrollConfirmations();
        assertNotNull(confirmation.read("lab-a",UUID.fromString(ticket.path("id").asText())));
    }

    @Test void r11InvalidIdentityAndForeignWorkspaceCannotClose() throws Exception {
        var ticket = awaitingConfirmation("r11-auth-owner-" + UUID.randomUUID());
        assertThrows(CoreException.class, () -> evaluate(ticket));
        authorizedTimerFixture(java.time.Instant.parse(ticket.path("confirmDeadlineAt").asText()));
        assertThrows(CoreException.class, () -> confirmationTimers.evaluate("lab-b",
            ticket.path("process").path("processInstanceId").asText(), ticket.path("confirmGeneration").asText()));
        mvc.perform(post("/api/hpdi/v1/workspaces/lab-a/tasks/" + ticket.path("confirmGeneration").asText() + "/complete")
            .with(as("not-requester","lab-a","coordinator")).contentType("application/json")
            .content("{\"expectedTaskKey\":\"confirm\",\"outcome\":\"accepted\"}")).andExpect(status().isForbidden());
        assertNull(confirmation.read("lab-a",UUID.fromString(ticket.path("id").asText())).closedBy());
    }

    @Test void r11SystemAuditFailureRollsBackRealEngineAndRecovers() throws Exception {
        var ticket = awaitingConfirmation("r11-rollback-owner-" + UUID.randomUUID());
        authorizedTimerFixture(java.time.Instant.parse(ticket.path("confirmDeadlineAt").asText()));
        jdbc.execute("create function r11_reject_audit() returns trigger language plpgsql as $$ begin "
            + "if new.action='task.timer.complete' then raise exception 'test rejection' using errcode='23514'; end if; return new; end $$");
        jdbc.execute("create trigger r11_reject before insert on audit_event for each row execute function r11_reject_audit()");
        try {
            assertThrows(RuntimeException.class, () -> evaluate(ticket));
            assertNull(confirmation.read("lab-a",UUID.fromString(ticket.path("id").asText())).closedBy());
            assertEquals(0,jdbc.queryForObject("select count(*) from audit_event where action='servicedesk.confirmation.autoclosed' and resource=?",
                Integer.class,ticket.path("id").asText()));
        } finally {
            jdbc.execute("drop trigger r11_reject on audit_event"); jdbc.execute("drop function r11_reject_audit()");
        }
        new TicketConfirmationWorker(jdbc,confirmationTimers,confirmationClock,true).run();
        assertEquals("AUTO",confirmation.read("lab-a",UUID.fromString(ticket.path("id").asText())).closedBy());
        new TicketConfirmationWorker(jdbc,confirmationTimers,confirmationClock,true).run();
        assertEquals(1,jdbc.queryForObject("select count(*) from audit_event where action='task.timer.complete' and resource=?",
            Integer.class,ticket.path("confirmGeneration").asText()));
    }

    @Test void r11ReopenMonitorIsDurableDeduplicatedAndStopsAtReviewedAssignment() throws Exception {
        String owner = "r11-reopen-owner-" + UUID.randomUUID();
        var ticket = awaitingConfirmation(owner);
        mvc.perform(post("/api/hpdi/v1/workspaces/lab-a/tasks/" + ticket.path("confirmGeneration").asText() + "/complete")
            .with(as(owner,"lab-a","requester")).contentType("application/json")
            .content("{\"expectedTaskKey\":\"confirm\",\"outcome\":\"rework\"}")).andExpect(status().isNoContent());
        var state = confirmation.read("lab-a",UUID.fromString(ticket.path("id").asText()));
        authorizedTimerFixture(state.confirmationHistory().get(0).reopenDeadlineAt());
        new TicketConfirmationWorker(jdbc,confirmationTimers,confirmationClock,true).run();
        var alerted = confirmation.read("lab-a",UUID.fromString(ticket.path("id").asText())).confirmationHistory().get(0).reopenAlertedAt();
        assertNotNull(alerted);
        new TicketConfirmationWorker(jdbc,confirmationTimers,confirmationClock,true).run();
        assertEquals(alerted,confirmation.read("lab-a",UUID.fromString(ticket.path("id").asText())).confirmationHistory().get(0).reopenAlertedAt());
        evaluate(ticket); evaluate(ticket);
        assertEquals(2,jdbc.queryForObject("select count(*) from hpdi_alert_event where business_key=? and type in ('REOPENED','REOPEN_UNCLAIMED')",
            Integer.class,ticket.path("id").asText()));
        assertNull(confirmation.read("lab-a",UUID.fromString(ticket.path("id").asText())).closedBy());
        review(ticket,"coordinator","r11-new-agent-"+UUID.randomUUID(),null).andExpect(status().isOk());
        assertEquals("ASSIGNED",confirmation.read("lab-a",UUID.fromString(ticket.path("id").asText())).confirmationHistory().get(0).reopenReceivedReason());
        evaluate(ticket);
        assertEquals(2,jdbc.queryForObject("select count(*) from hpdi_alert_event where business_key=? and type in ('REOPENED','REOPEN_UNCLAIMED')",
            Integer.class,ticket.path("id").asText()));
    }

    @Test void r11UnclaimedPublicationAndCursorRollbackTogether() throws Exception {
        String owner = "r11-alert-rollback-" + UUID.randomUUID();
        var ticket = awaitingConfirmation(owner);
        mvc.perform(post("/api/hpdi/v1/workspaces/lab-a/tasks/" + ticket.path("confirmGeneration").asText() + "/complete")
            .with(as(owner,"lab-a","requester")).contentType("application/json")
            .content("{\"expectedTaskKey\":\"confirm\",\"outcome\":\"rework\"}")).andExpect(status().isNoContent());
        var id = UUID.fromString(ticket.path("id").asText());
        authorizedTimerFixture(confirmation.read("lab-a",id).confirmationHistory().get(0).reopenDeadlineAt());
        jdbc.execute("create function r11_alert_reject() returns trigger language plpgsql as $$ begin "
            + "if new.action='human.alert.created' then raise exception 'test rejection'; end if; return new; end $$");
        jdbc.execute("create trigger r11_alert_reject before insert on audit_event for each row execute function r11_alert_reject()");
        try {
            assertThrows(RuntimeException.class, () -> evaluate(ticket));
            assertNull(confirmation.read("lab-a",id).confirmationHistory().get(0).reopenAlertedAt());
            assertEquals(0,jdbc.queryForObject("select count(*) from hpdi_alert_event where business_key=? and type='REOPEN_UNCLAIMED'",Integer.class,id.toString()));
        } finally {
            jdbc.execute("drop trigger r11_alert_reject on audit_event"); jdbc.execute("drop function r11_alert_reject()");
        }
        evaluate(ticket);
        assertNotNull(confirmation.read("lab-a",id).confirmationHistory().get(0).reopenAlertedAt());
        assertEquals(1,jdbc.queryForObject("select count(*) from hpdi_alert_event where business_key=? and type='REOPEN_UNCLAIMED'",Integer.class,id.toString()));
    }

    @Test void r11ReopenQueueAdvancesPastOneBatchWithoutRepollingAlertedRows() throws Exception {
        String owner = "r11-fair-"+UUID.randomUUID();
        var ticket = awaitingConfirmation(owner);
        String generation = ticket.path("confirmGeneration").asText();
        mvc.perform(post("/api/hpdi/v1/workspaces/lab-a/tasks/"+generation+"/complete")
            .with(as(owner,"lab-a","requester")).contentType("application/json")
            .content("{\"expectedTaskKey\":\"confirm\",\"outcome\":\"rework\"}")).andExpect(status().isNoContent());
        String prefix = "fair-"+UUID.randomUUID()+"-";
        var due = confirmation.read("lab-a",UUID.fromString(ticket.path("id").asText())).confirmationHistory().get(0).reopenDeadlineAt();
        var seen = new java.util.HashSet<String>();
        vn.haui.dx.hpdi.process.api.ProcessTimers sink = (workspace, process, item) -> {
            if (!item.startsWith(prefix)) return;
            assertTrue(seen.add(item),"Already alerted generation selected again");
            jdbc.update("update servicedesk_confirmation set reopen_alerted_at=? where generation=?",java.sql.Timestamp.from(due),item);
        };
        try {
            jdbc.update("insert into servicedesk_confirmation(workspace,ticket_id,process_instance_id,generation,handle_task_id,handle_resolved_at,confirm_started_at,deadline_at,decision,decided_at,decided_by,reopen_deadline_at,checked_at)"
                + " select workspace,ticket_id,process_instance_id,?||round::text,handle_task_id,handle_resolved_at,confirm_started_at,deadline_at,decision,decided_at,decided_by,reopen_deadline_at,'2000-01-01'::timestamptz"
                + " from servicedesk_confirmation cross join generate_series(1,105) as round where generation=?",prefix,generation);
            var worker = new TicketConfirmationWorker(jdbc,sink,java.time.Clock.fixed(due,java.time.ZoneOffset.UTC),true);
            worker.run(); assertEquals(100,seen.size());
            worker.run(); assertEquals(105,seen.size());
            worker.run(); assertEquals(105,seen.size());
        } finally {
            jdbc.update("delete from servicedesk_confirmation where generation like ?",prefix+"%");
        }
    }

    @Test void r11AutoCloseWorkerAndScheduleCancellationDoNotInvertLocks() throws Exception {
        var ticket=awaitingConfirmation("r3-r11-owner-"+UUID.randomUUID());
        String process=ticket.path("process").path("processInstanceId").asText();
        String agent=jdbc.queryForObject("select assignee_ from act_hi_taskinst where proc_inst_id_=? and task_def_key_='handle'",String.class,process);
        authorizedTimerFixture(java.time.Instant.parse(ticket.path("confirmDeadlineAt").asText()));
        var executor=Executors.newSingleThreadExecutor();
        try (var blocker=java.sql.DriverManager.getConnection(System.getenv("FILE_TEST_JDBC_URL"),"workflow",System.getenv("FILE_TEST_DB_PASSWORD"))) {
            blocker.setAutoCommit(false);
            int blockerPid;
            try (var statement=blocker.createStatement(); var rows=statement.executeQuery("select pg_backend_pid()")) {
                assertTrue(rows.next()); blockerPid=rows.getInt(1);
            }
            try (var statement=blocker.prepareStatement("select id from process_scope where id=? for update")) {
                statement.setString(1,process);
                try (var rows=statement.executeQuery()) { assertTrue(rows.next()); }
            }
            var worker=executor.submit(() -> new TicketConfirmationWorker(jdbc,confirmationTimers,confirmationClock,true).run());
            int waiting=0;
            long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(15);
            while (waiting==0 && System.nanoTime()<deadline) {
                waiting=jdbc.queryForObject("select count(*) from pg_stat_activity where ?=any(pg_blocking_pids(pid))",Integer.class,blockerPid);
                if (waiting==0) Thread.sleep(20);
            }
            assertTrue(waiting>0,"Real R11 worker must wait at the process row lock");
            mvc.perform(put("/api/hpdi/v1/workspaces/lab-a/schedules/"+agent).with(as("coordinator","lab-a","coordinator"))
                .contentType("application/json").content("{\"version\":0,\"enabled\":true,\"shifts\":[]}"))
                .andExpect(status().isOk());
            blocker.commit();
            worker.get(30,TimeUnit.SECONDS);
        } finally { executor.shutdownNow(); }
        evaluate(ticket);
        assertEquals("AUTO",confirmation.read("lab-a",UUID.fromString(ticket.path("id").asText())).closedBy());
        assertEquals(1,jdbc.queryForObject("select count(*) from audit_event where action='task.timer.complete' and resource=?",Integer.class,ticket.path("confirmGeneration").asText()));
    }

    @Test void r11AutoCloseDeadlineGenerationAndRepeatedWorkerAreAtomic() throws Exception {
        var ticket = awaitingConfirmation("r11-owner-" + UUID.randomUUID());
        var start = java.time.Instant.parse(ticket.path("confirmStartedAt").asText());
        var deadline = java.time.Instant.parse(ticket.path("confirmDeadlineAt").asText());
        assertEquals(Duration.ofHours(72), Duration.between(start, deadline));
        var preserved = jdbc.queryForList("select * from servicedesk_sla where ticket_id=?", UUID.fromString(ticket.path("id").asText()));
        authorizedTimerFixture(deadline.minusMillis(1));
        evaluate(ticket);
        assertNull(confirmation.read("lab-a", UUID.fromString(ticket.path("id").asText())).closedBy());
        authorizedTimerFixture(deadline);
        confirmationTimers.evaluate("lab-a",ticket.path("process").path("processInstanceId").asText(),"stale-generation");
        assertNull(confirmation.read("lab-a", UUID.fromString(ticket.path("id").asText())).closedBy());
        var executor = Executors.newFixedThreadPool(2);
        try {
            var first = executor.submit(() -> evaluate(ticket));
            var second = executor.submit(() -> evaluate(ticket));
            first.get(20, TimeUnit.SECONDS); second.get(20, TimeUnit.SECONDS);
        } finally { executor.shutdownNow(); }
        evaluate(ticket);
        var result = confirmation.read("lab-a", UUID.fromString(ticket.path("id").asText()));
        assertEquals("AUTO", result.closedBy());
        assertEquals(deadline, result.closedAt());
        assertEquals(preserved,jdbc.queryForList("select * from servicedesk_sla where ticket_id=?", UUID.fromString(ticket.path("id").asText())));
        assertEquals(1,jdbc.queryForObject("select count(*) from audit_event where action='task.timer.complete' and resource=?",Integer.class,ticket.path("confirmGeneration").asText()));
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings={"accepted","rework"})
    void r11RequesterAndAutoCloseRaceHasOneDecision(String outcome) throws Exception {
        String owner = "r11-race-owner-" + UUID.randomUUID();
        var ticket = awaitingConfirmation(owner);
        authorizedTimerFixture(java.time.Instant.parse(ticket.path("confirmDeadlineAt").asText()));
        var start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var timer = executor.submit(() -> { start.await(); evaluate(ticket); return null; });
            var requester = executor.submit(() -> {
                start.await();
                return mvc.perform(post("/api/hpdi/v1/workspaces/lab-a/tasks/" + ticket.path("confirmGeneration").asText() + "/complete")
                    .with(as(owner,"lab-a","requester")).contentType("application/json")
                    .content("{\"expectedTaskKey\":\"confirm\",\"outcome\":\"" + outcome + "\"}"))
                    .andReturn().getResponse().getStatus();
            });
            start.countDown(); timer.get(20,TimeUnit.SECONDS);
            int status = requester.get(20,TimeUnit.SECONDS);
            assertTrue(status == 204 || status == 409, "Unexpected race response " + status);
            var state = confirmation.read("lab-a",UUID.fromString(ticket.path("id").asText()));
            assertEquals(1,state.confirmationHistory().size());
            assertEquals(status == 409 ? "AUTO" : "accepted".equals(outcome) ? "REQUESTER" : "REWORK",state.confirmationHistory().get(0).decision());
        } finally { executor.shutdownNow(); }
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings={"NOT_STARTED","IN_PROGRESS","PENDING"})
    void r9CoordinatorCategoryCorrectionPreservesProcessAndWorkState(String state) throws Exception {
        String worker = "r9-agent-"+UUID.randomUUID();
        var ticket = create("r9-owner-"+UUID.randomUUID(),"R9 category","requester");
        var reviewed = body(review(ticket,"coordinator",worker,null).andExpect(status().isOk()));
        String task = reviewed.path("taskId").asText();
        String actions = "/api/hpdi/v1/workspaces/lab-a/tasks/"+task;
        if (!"NOT_STARTED".equals(state)) {
            mvc.perform(post(actions+"/start").with(as(worker,"lab-a","agent"))).andExpect(status().isOk());
            if ("PENDING".equals(state)) mvc.perform(post(actions+"/pause").with(as(worker,"lab-a","agent"))
                .contentType("application/json").content("{\"reason\":\"WAITING_REQUESTER\"}")).andExpect(status().isOk());
        }
        var before = jdbc.queryForMap("select * from servicedesk_sla where handle_task_id=?",task);
        var monitor = jdbc.queryForMap("select * from servicedesk_alert_monitor where handle_task_id=?",task);
        var corrected = body(mvc.perform(patch(BASE+"/"+ticket.path("id").asText()+"/classification")
            .with(as("coordinator","lab-a","coordinator")).contentType("application/json")
            .content("{\"category\":\"NETWORK\",\"reason\":\"Verified network fault\"}")).andExpect(status().isOk()));
        assertEquals(ticket.path("process"),corrected.path("process"));
        assertEquals("NETWORK",corrected.path("classification").path("category").asText());
        assertEquals(1,corrected.path("corrections").size());
        assertFalse(corrected.path("corrections").get(0).path("fromAi").asBoolean());
        var after = jdbc.queryForMap("select * from servicedesk_sla where handle_task_id=?",task);
        for (var field : List.of("approved_at","start_by","resolve_by","first_started_at")) assertEquals(before.get(field),after.get(field));
        if ("NOT_STARTED".equals(state)) {
            assertEquals("AWAITING_REVIEW",corrected.path("state").asText());
            assertNotEquals(task,corrected.path("taskId").asText());
            var next = body(review(ticket,"coordinator","r9-next-agent",null,"NETWORK","HIGH").andExpect(status().isOk()));
            assertEquals(before.get("approved_at"),jdbc.queryForObject("select approved_at from servicedesk_sla where handle_task_id=?",java.sql.Timestamp.class,next.path("taskId").asText()));
        } else {
            assertEquals(task,corrected.path("taskId").asText());
            assertEquals(state,corrected.path("workState").asText());
            assertEquals(worker,corrected.path("assignee").asText());
            assertEquals(monitor.get("pending_at"),jdbc.queryForObject("select pending_at from servicedesk_alert_monitor where handle_task_id=?",java.sql.Timestamp.class,task));
        }
        assertEquals(1,jdbc.queryForObject("select count(*) from hpdi_alert_event where task_id=? and type='CLASSIFICATION_CHANGED'",Integer.class,task));
    }

    @Test void r9PriorityCorrectionSupersedesEtAndSynchronizesNativeSchedule() throws Exception {
        var ticket = create("r9-priority-owner-"+UUID.randomUUID(),"R9 priority","requester");
        var reviewed = body(review(ticket,"coordinator","r9-agent",null).andExpect(status().isOk()));
        String task = reviewed.path("taskId").asText();
        String tasks = "/api/hpdi/v1/workspaces/lab-a/tasks/";
        var request = body(mvc.perform(post(tasks+task+"/estimate-requests").with(as("r9-agent","lab-a","agent"))
            .contentType("application/json").content("{\"proposedMinutes\":150,\"reason\":\"More diagnostics\"}")).andExpect(status().isCreated()));
        var before = jdbc.queryForMap("select * from servicedesk_sla where handle_task_id=?",task);
        var corrected = body(mvc.perform(patch(BASE+"/"+ticket.path("id").asText()+"/classification")
            .with(as("r9-agent","lab-a","agent")).contentType("application/json")
            .content("{\"priority\":\"CRITICAL\",\"reason\":\"Confirmed impact\"}")).andExpect(status().isOk()));
        assertEquals("CRITICAL",corrected.path("classification").path("priority").asText());
        assertEquals(120,corrected.path("estimate").path("minutes").asInt());
        var after = jdbc.queryForMap("select * from servicedesk_sla where handle_task_id=?",task);
        assertEquals(before.get("approved_at"),after.get("approved_at"));
        assertEquals(before.get("policy"),after.get("policy"));
        assertEquals(before.get("base_priority"),after.get("base_priority"));
        assertEquals("CLASSIFICATION_CHANGED",jdbc.queryForObject("select superseded_reason from hpdi_estimate_request where id=?",String.class,UUID.fromString(request.path("id").asText())));
        assertEquals(100,jdbc.queryForObject("select priority_ from act_ru_task where id_=?",Integer.class,task));
        assertEquals(((java.sql.Timestamp)after.get("resolve_by")).getTime(),jdbc.queryForObject("select due_date_ from act_ru_task where id_=?",java.sql.Timestamp.class,task).getTime());
        mvc.perform(get(BASE+"/"+ticket.path("id").asText()).with(as(ticket.path("creator").asText(),"lab-a","requester")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.corrections").doesNotExist());
        mvc.perform(post("/api/hpdi/v1/workspaces/lab-a/estimate-requests/"+request.path("id").asText()+"/decision")
            .with(as("coordinator","lab-a","coordinator")).contentType("application/json").content("{\"approved\":true}"))
            .andExpect(status().isConflict());
        mvc.perform(post(tasks+task+"/estimate-requests").with(as("r9-agent","lab-a","agent"))
            .contentType("application/json").content("{\"proposedMinutes\":150,\"reason\":\"New classification generation\"}"))
            .andExpect(status().isCreated());
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings={"DEFAULT","MANUAL","COORDINATOR","HANDOVER"})
    void r9DefaultEstimateUsesConfirmedPriorityAcrossEscalationAndHandover(String mode) throws Exception {
        String worker = "r9-business-"+UUID.randomUUID();
        var ticket = create("r9-business-owner-"+UUID.randomUUID(),"Business priority","requester");
        UUID id = UUID.fromString(ticket.path("id").asText());
        var reviewed = body(review(ticket,"coordinator",worker,"COORDINATOR".equals(mode) ? 77 : null,"OTHER","LOW")
            .andExpect(status().isOk()));
        String task = reviewed.path("taskId").asText();
        jdbc.update("update servicedesk_sla set approved_at=approved_at-interval '10 days',start_by=start_by-interval '10 days',"
            + "resolve_by=resolve_by-interval '10 days' where handle_task_id=?",task);
        var initial = slaService.read("lab-a",id);
        slaService.reconcile("lab-a",initial.processId(),task);
        assertEquals("MEDIUM",slaService.read("lab-a",id).priority().name());
        if ("HANDOVER".equals(mode)) mvc.perform(post("/api/hpdi/v1/workspaces/lab-a/tasks/"+task+"/start")
            .with(as(worker,"lab-a","agent"))).andExpect(status().isOk());
        String priority = "LOW";
        String route = BASE+"/"+id+"/classification";
        if ("MANUAL".equals(mode) || "COORDINATOR".equals(mode)) {
            mvc.perform(patch(route).with(as("coordinator","lab-a","coordinator")).contentType("application/json")
                .content("{\"priority\":\"HIGH\",\"reason\":\"Human confirmed impact\"}")).andExpect(status().isOk());
            priority = "HIGH";
        }
        var corrected = body(mvc.perform(patch(route).with(as("coordinator","lab-a","coordinator"))
            .contentType("application/json").content("{\"category\":\"NETWORK\",\"reason\":\"Confirmed category\"}"))
            .andExpect(status().isOk()));
        int expected = "COORDINATOR".equals(mode) ? 77
            : TicketReview.defaultMinutes(TicketReview.Category.NETWORK,TicketReview.Priority.valueOf(priority));
        assertEquals(expected,corrected.path("estimate").path("minutes").asInt());
        assertEquals(priority,jdbc.queryForObject("select priority from servicedesk_review where ticket_id=?",String.class,id));
        if ("HANDOVER".equals(mode)) mvc.perform(post("/api/hpdi/v1/workspaces/lab-a/tasks/"+task+"/release")
            .with(as(worker,"lab-a","agent")).contentType("application/json")
            .content("{\"reason\":\"HANDOVER\",\"note\":\"Continue after correction\"}")).andExpect(status().isNoContent());
        var next = body(review(ticket,"coordinator",worker,null,"NETWORK",priority).andExpect(status().isOk()));
        assertEquals(expected,next.path("estimate").path("minutes").asInt());
        assertEquals(initial.approvedAt(),slaService.read("lab-a",id).approvedAt());
        assertEquals("COORDINATOR".equals(mode) ? "COORDINATOR" : "DEFAULT",next.path("estimate").path("basis").asText());
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings={"HANDOVER","REWORK","RECONCILE","ROLLBACK"})
    void r9ManualReaffirmationOverridesEscalationWithoutFalseFieldHistory(String mode) throws Exception {
        String worker = "r9-reaffirm-"+UUID.randomUUID();
        String owner = "r9-reaffirm-owner-"+UUID.randomUUID();
        var ticket = create(owner,"Reaffirm business priority","requester");
        UUID id = UUID.fromString(ticket.path("id").asText());
        var reviewed = body(review(ticket,"coordinator",worker,null,"OTHER","LOW").andExpect(status().isOk()));
        String task = reviewed.path("taskId").asText();
        String tasks = "/api/hpdi/v1/workspaces/lab-a/tasks/";
        jdbc.update("update servicedesk_sla set approved_at=approved_at-interval '10 days',start_by=start_by-interval '10 days',"
            + "resolve_by=resolve_by-interval '10 days' where handle_task_id=?",task);
        var initial = slaService.read("lab-a",id);
        slaService.reconcile("lab-a",initial.processId(),task);
        assertEquals("MEDIUM",slaService.read("lab-a",id).priority().name());
        var before = jdbc.queryForMap("select * from servicedesk_sla where handle_task_id=?",task);
        var correction = patch(BASE+"/"+id+"/classification").with(as("coordinator","lab-a","coordinator"))
            .contentType("application/json").content("{\"priority\":\"LOW\",\"reason\":\"Manually reaffirm LOW\"}");
        if ("ROLLBACK".equals(mode)) {
            jdbc.execute("create function r9_reject_override() returns trigger language plpgsql as $$ begin if new.action='servicedesk.sla.priority.overridden' then raise exception 'override audit rejection' using errcode='23514'; end if; return new; end $$");
            jdbc.execute("create trigger r9_reject_override before insert on audit_event for each row execute function r9_reject_override()");
            try {
                mvc.perform(correction).andExpect(status().isConflict());
                assertEquals(before,jdbc.queryForMap("select * from servicedesk_sla where handle_task_id=?",task));
                assertEquals(0,jdbc.queryForObject("select count(*) from servicedesk_sla_amendment where ticket_id=?",Integer.class,id));
                assertEquals(0,jdbc.queryForObject("select count(*) from servicedesk_classification_correction where ticket_id=?",Integer.class,id));
            } finally {
                jdbc.execute("drop trigger r9_reject_override on audit_event");
                jdbc.execute("drop function r9_reject_override()");
            }
            return;
        }
        if ("RECONCILE".equals(mode)) {
            var pool = Executors.newFixedThreadPool(2);
            var gate = new CountDownLatch(1);
            try {
                var override = pool.submit(() -> { assertTrue(gate.await(15,TimeUnit.SECONDS));
                    return mvc.perform(correction).andReturn().getResponse().getStatus(); });
                var reconciliation = pool.submit(() -> { assertTrue(gate.await(15,TimeUnit.SECONDS));
                    for (int attempt=0; attempt<10; attempt++) slaService.reconcile("lab-a",initial.processId(),task);
                    return null; });
                gate.countDown();
                assertEquals(200,override.get(30,TimeUnit.SECONDS));
                reconciliation.get(30,TimeUnit.SECONDS);
            } finally { gate.countDown(); pool.shutdownNow(); }
        } else mvc.perform(correction).andExpect(status().isOk());
        mvc.perform(correction).andExpect(status().isConflict());
        slaService.reconcile("lab-a",initial.processId(),task);
        var amended = slaService.read("lab-a",id);
        assertTrue(amended.manualPriority());
        assertEquals("LOW",amended.priority().name());
        assertEquals("LOW",jdbc.queryForObject("select priority from servicedesk_review where ticket_id=?",String.class,id));
        var after = jdbc.queryForMap("select * from servicedesk_sla where handle_task_id=?",task);
        for (var field : List.of("approved_at","start_by","resolve_by","policy","base_priority",
                "start_budget_adjustment_millis","resolve_budget_adjustment_millis")) assertEquals(before.get(field),after.get(field),field);
        assertEquals("LOW",after.get("manual_priority"));
        assertTrue(amended.project(slaService.now()).startBreached());
        assertTrue(amended.project(slaService.now()).resolveBreached());
        assertEquals(0,jdbc.queryForObject("select count(*) from servicedesk_classification_correction where ticket_id=?",Integer.class,id));
        assertEquals(1,jdbc.queryForObject("select count(*) from servicedesk_sla_amendment where ticket_id=? and before_state->>'priority'='MEDIUM'"
            + " and after_state->>'manual_priority'='LOW' and after_state->>'priority'='LOW'",Integer.class,id));
        var provenance = json.readTree(jdbc.queryForObject("select details::text from audit_event where action='servicedesk.sla.priority.overridden' and resource=?",String.class,id.toString()));
        assertEquals("Manually reaffirm LOW",jdbc.queryForObject("select reason from servicedesk_estimate_history where id=?",String.class,
            provenance.path("estimateHistoryId").asLong()));
        assertEquals("MEDIUM",provenance.path("previousEffectivePriorityKey").asText());
        assertEquals("LOW",provenance.path("manualPriorityKey").asText());
        assertTrue(provenance.path("reaffirmedKey").asBoolean());
        assertEquals(25,jdbc.queryForObject("select priority_ from act_ru_task where id_=?",Integer.class,task));
        mvc.perform(get("/api/hpdi/v1/workspaces/lab-a/queue").with(as(worker,"lab-a","agent")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].priority").value("LOW"));
        var corrected = body(mvc.perform(get(BASE+"/"+id).with(as("coordinator","lab-a","coordinator"))).andExpect(status().isOk()));
        assertEquals(reviewed.path("estimate"),corrected.path("estimate"));
        assertEquals(0,corrected.path("corrections").size());
        if ("HANDOVER".equals(mode)) {
            mvc.perform(post(tasks+task+"/release").with(as(worker,"lab-a","agent")).contentType("application/json")
                .content("{\"reason\":\"HANDOVER\",\"note\":\"Retain manual LOW\"}")).andExpect(status().isNoContent());
            review(ticket,"coordinator",worker,null,"OTHER","LOW").andExpect(status().isOk());
            var continued = slaService.read("lab-a",id);
            assertTrue(continued.manualPriority());
            assertEquals(amended.approvedAt(),continued.approvedAt());
            assertEquals(amended.resolveBy(),continued.resolveBy());
            slaService.reconcile("lab-a",continued.processId(),continued.taskId());
            assertEquals("LOW",slaService.read("lab-a",id).priority().name());
        } else if ("REWORK".equals(mode)) {
            mvc.perform(post(tasks+task+"/start").with(as(worker,"lab-a","agent"))).andExpect(status().isOk());
            mvc.perform(post(tasks+task+"/complete").with(as(worker,"lab-a","agent")).contentType("application/json")
                .content("{\"expectedTaskKey\":\"handle\",\"outcome\":\"resolved\"}")).andExpect(status().isNoContent());
            mvc.perform(post(tasks+currentTask(ticket,owner)+"/complete").with(as(owner,"lab-a","requester")).contentType("application/json")
                .content("{\"expectedTaskKey\":\"confirm\",\"outcome\":\"rework\"}")).andExpect(status().isNoContent());
            review(ticket,"coordinator",worker,null,"OTHER","LOW").andExpect(status().isOk());
            var fresh = slaService.read("lab-a",id);
            assertFalse(fresh.manualPriority());
            assertTrue(fresh.approvedAt().isAfter(amended.approvedAt()));
            assertFalse(fresh.project(slaService.now()).resolveBreached());
            assertEquals("LOW",jdbc.queryForObject("select manual_priority from servicedesk_sla where handle_task_id=?",String.class,task));
        }
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans={false,true})
    void r9CriticalCorrectionPublishesOneDistinctDurableCoordinatorEvent(boolean categoryChanged) throws Exception {
        var ticket = create("r9-critical-owner-"+UUID.randomUUID(),"Critical correction","requester");
        var reviewed = body(review(ticket,"coordinator","r9-critical-"+UUID.randomUUID(),null).andExpect(status().isOk()));
        String task = reviewed.path("taskId").asText();
        String route = BASE+"/"+ticket.path("id").asText()+"/classification";
        String input = categoryChanged ? "{\"category\":\"NETWORK\",\"priority\":\"CRITICAL\",\"reason\":\"Verified\"}"
            : "{\"priority\":\"CRITICAL\",\"reason\":\"Verified\"}";
        mvc.perform(patch(route).with(as("coordinator","lab-a","coordinator")).contentType("application/json").content(input))
            .andExpect(status().isOk());
        var events = jdbc.queryForList("select * from hpdi_alert_event where task_id=? and type='CLASSIFICATION_CHANGED'",task);
        assertEquals(1,events.size());
        assertEquals("CRITICAL",events.get(0).get("severity"));
        assertTrue(events.get(0).get("label").toString().contains("Priority corrected to CRITICAL"));
        assertNull(events.get(0).get("agent"));
        if (!categoryChanged) mvc.perform(patch(route).with(as("coordinator","lab-a","coordinator"))
            .contentType("application/json").content(input)).andExpect(status().isConflict());
        assertEquals(1,jdbc.queryForObject("select count(*) from hpdi_alert_event where task_id=? and type='CLASSIFICATION_CHANGED'",Integer.class,task));
    }

    @Test void r9NoOpValidationAndRevokedMembershipDoNotCreateHistory() throws Exception {
        var ticket = create("r9-validation-owner-"+UUID.randomUUID(),"R9 validation","requester");
        review(ticket,"coordinator","r9-agent",45).andExpect(status().isOk());
        String route = BASE+"/"+ticket.path("id").asText()+"/classification";
        for (var invalid : List.of("{}","{\"reason\":\"Only reason\"}","{\"priority\":\"URGENT\",\"reason\":\"Invalid enum\"}",
                "{\"priority\":\"LOW\",\"reason\":\" \"}"))
            mvc.perform(patch(route).with(as("r9-agent","lab-a","agent")).contentType("application/json").content(invalid)).andExpect(status().isBadRequest());
        mvc.perform(patch(route).with(as("r9-agent","lab-a","agent")).contentType("application/json")
            .content("{\"priority\":\"HIGH\",\"reason\":\"Unchanged\"}")).andExpect(status().isConflict());
        mvc.perform(patch(route).with(as("other-agent","lab-a","agent")).contentType("application/json")
            .content("{\"priority\":\"LOW\",\"reason\":\"Not authorized\"}")).andExpect(status().isForbidden());
        when(directory.members("lab-a")).thenReturn(List.of());
        mvc.perform(patch(route).with(as("coordinator","lab-a","coordinator")).contentType("application/json")
            .content("{\"priority\":\"LOW\",\"reason\":\"Revoked role\"}")).andExpect(status().isForbidden());
        assertEquals(0,jdbc.queryForObject("select count(*) from servicedesk_classification_correction where ticket_id=?",Integer.class,UUID.fromString(ticket.path("id").asText())));
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"NOT_STARTED", "IN_PROGRESS", "PENDING"})
    void r6HandoverReturnsRealReviewAndPreservesWorkAndDeadline(String state) throws Exception {
        String worker = "handover-" + UUID.randomUUID();
        var ticket = create("owner-" + UUID.randomUUID(), "Handover", "requester");
        var reviewed = body(review(ticket, "coordinator", worker, 90).andExpect(status().isOk()));
        String task = reviewed.path("taskId").asText();
        String tasks = "/api/hpdi/v1/workspaces/lab-a/tasks/";
        if (!"NOT_STARTED".equals(state)) {
            mvc.perform(post(tasks + task + "/start").with(as(worker,"lab-a","agent"))).andExpect(status().isOk());
            engineState.elapse(task, 1_200_000);
            if ("PENDING".equals(state)) mvc.perform(post(tasks + task + "/pause").with(as(worker,"lab-a","agent"))
                .contentType("application/json").content("{\"reason\":\"WAITING_EXTERNAL\"}")).andExpect(status().isOk());
        }
        var old = jdbc.queryForMap("select * from servicedesk_sla where handle_task_id=?", task);
        mvc.perform(post(tasks + task + "/release").with(as(worker,"lab-a","agent")).contentType("application/json")
            .content("{\"reason\":\"HANDOVER\",\"note\":\"Hardware checked; continue diagnostics\"}")).andExpect(status().isNoContent());
        UUID id = UUID.fromString(ticket.path("id").asText());
        var released = jdbc.queryForMap("select * from servicedesk_work_release where task_id=?", task);
        assertEquals(((Number) released.get("worked_millis")).longValue(), jdbc.queryForObject("select worked_millis from servicedesk_ticket where id=?", Long.class, id));
        assertNotNull(jdbc.queryForObject("select released_at from servicedesk_sla where handle_task_id=?", java.sql.Timestamp.class, task));
        var awaiting = body(mvc.perform(get(BASE + "/" + id).with(as("coordinator","lab-a","coordinator"))).andExpect(status().isOk()));
        assertEquals("AWAITING_REVIEW", awaiting.path("state").asText());
        assertEquals(ticket.path("process"), awaiting.path("process"));
        assertEquals(0, jdbc.queryForObject("select count(*) from act_ru_task where id_=?", Integer.class, task));
        String nextWorker = "next-" + UUID.randomUUID();
        var next = body(review(ticket,"coordinator",nextWorker,null).andExpect(status().isOk()));
        String nextTask = next.path("taskId").asText();
        assertNotEquals(task,nextTask);
        assertEquals(90,next.path("estimate").path("minutes").asInt());
        var carried = jdbc.queryForMap("select * from servicedesk_sla where handle_task_id=?", nextTask);
        assertEquals(old.get("approved_at"),carried.get("approved_at"));
        assertEquals(old.get("start_by"),carried.get("start_by"));
        if (!"PENDING".equals(state)) assertEquals(old.get("resolve_by"),carried.get("resolve_by"));
        assertEquals(1, jdbc.queryForObject("select count(*) from act_ru_task where proc_inst_id_=?", Integer.class, ticket.path("process").path("processInstanceId").asText()));
        mvc.perform(post(tasks + nextTask + "/start").with(as(nextWorker,"lab-a","agent"))).andExpect(status().isOk());
        if (!"NOT_STARTED".equals(state)) assertTrue(((Number) released.get("worked_millis")).longValue() >= 1_200_000);
        engineState.elapse(nextTask,600_000);
        mvc.perform(post(tasks + nextTask + "/complete").with(as(nextWorker,"lab-a","agent")).contentType("application/json")
            .content("{\"expectedTaskKey\":\"handle\",\"outcome\":\"resolved\"}")).andExpect(status().isNoContent());
        long total = jdbc.queryForObject("select worked_millis from servicedesk_ticket where id=?",Long.class,id);
        long previous = ((Number) released.get("worked_millis")).longValue();
        assertTrue(total >= previous + 600_000 && total < previous + 620_000);
    }

    @Test void r2EstimateApprovalRejectDuplicateAndStaleAreAtomic() throws Exception {
        String worker = "estimate-" + UUID.randomUUID();
        var ticket = create("owner-" + UUID.randomUUID(),"Estimate", "requester");
        var reviewed = body(review(ticket,"coordinator",worker,90).andExpect(status().isOk()));
        String task = reviewed.path("taskId").asText();
        String base = "/api/hpdi/v1/workspaces/lab-a/";
        String propose = base + "tasks/" + task + "/estimate-requests";
        String payload = "{\"proposedMinutes\":120,\"reason\":\"Replace mainboard\"}";
        var request = body(mvc.perform(post(propose).with(as(worker,"lab-a","agent")).contentType("application/json").content(payload)).andExpect(status().isCreated()));
        assertEquals(90,request.path("currentMinutes").asInt());
        mvc.perform(post(propose).with(as(worker,"lab-a","agent")).contentType("application/json").content(payload)).andExpect(status().isConflict());
        mvc.perform(get(base + "estimate-requests").with(as("owner","lab-a","requester"))).andExpect(status().isForbidden());
        mvc.perform(get(base + "estimate-requests").with(as("other","lab-a","agent"))).andExpect(status().isForbidden());
        mvc.perform(post(base + "estimate-requests/" + request.path("id").asText() + "/decision").with(as("coordinator","lab-a","coordinator"))
            .contentType("application/json").content("{\"approved\":true}")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("APPROVED"));
        mvc.perform(post(base + "estimate-requests/" + request.path("id").asText() + "/decision").with(as("coordinator","lab-a","coordinator"))
            .contentType("application/json").content("{\"approved\":false}")).andExpect(status().isConflict());
        UUID id = UUID.fromString(ticket.path("id").asText());
        assertEquals(120,jdbc.queryForObject("select estimate_minutes from servicedesk_review where ticket_id=?",Integer.class,id));
        var second = body(mvc.perform(post(propose).with(as(worker,"lab-a","agent")).contentType("application/json").content(payload)).andExpect(status().isCreated()));
        mvc.perform(post(base + "estimate-requests/" + second.path("id").asText() + "/decision").with(as("coordinator","lab-a","coordinator"))
            .contentType("application/json").content("{\"approved\":false,\"note\":\"Keep estimate\"}")).andExpect(status().isOk());
        var stale = body(mvc.perform(post(propose).with(as(worker,"lab-a","agent")).contentType("application/json").content(payload)).andExpect(status().isCreated()));
        mvc.perform(post(base + "tasks/" + task + "/release").with(as(worker,"lab-a","agent")).contentType("application/json")
            .content("{\"reason\":\"HANDOVER\",\"note\":\"Continue diagnostics\"}")).andExpect(status().isNoContent());
        mvc.perform(post(base + "estimate-requests/" + stale.path("id").asText() + "/decision").with(as("coordinator","lab-a","coordinator"))
            .contentType("application/json").content("{\"approved\":true}")).andExpect(status().isConflict());
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"NOT_STARTED", "PENDING"})
    void r2ReassignmentSupersedesWithoutFakeDecisionAndNeverRevives(String state) throws Exception {
        String worker = "old-" + UUID.randomUUID(), next = "new-" + UUID.randomUUID();
        var ticket = create("owner-" + UUID.randomUUID(), "Reassign estimate", "requester");
        String task = body(review(ticket,"coordinator",worker,90).andExpect(status().isOk())).path("taskId").asText();
        estimateMembers(worker,next);
        String base = "/api/hpdi/v1/workspaces/lab-a/";
        String requests = base + "tasks/" + task + "/estimate-requests";
        String payload = "{\"proposedMinutes\":120,\"reason\":\"More work\"}";
        String request = body(mvc.perform(post(requests).with(as(worker,"lab-a","agent"))
            .contentType("application/json").content(payload)).andExpect(status().isCreated())).path("id").asText();
        if ("PENDING".equals(state)) {
            mvc.perform(post(base + "tasks/" + task + "/start").with(as(worker,"lab-a","agent"))).andExpect(status().isOk());
            mvc.perform(post(base + "tasks/" + task + "/pause").with(as(worker,"lab-a","agent"))
                .contentType("application/json").content("{\"reason\":\"WAITING_EXTERNAL\"}")).andExpect(status().isOk());
        }
        var clocks = jdbc.queryForMap("select * from servicedesk_sla where handle_task_id=?",task);
        assignEstimate(task,worker).andExpect(status().isOk());
        assertEquals("PENDING",jdbc.queryForObject("select status from hpdi_estimate_request where id=?::uuid",String.class,request));
        assignEstimate(task,next).andExpect(status().isOk());
        var row = jdbc.queryForMap("select * from hpdi_estimate_request where id=?::uuid",request);
        assertEquals("SUPERSEDED",row.get("status"));
        assertEquals("coordinator",row.get("superseded_by"));
        assertNotNull(row.get("superseded_at"));
        assertEquals("ASSIGNMENT_CHANGED",row.get("superseded_reason"));
        assertNull(row.get("decided_by")); assertNull(row.get("decided_at"));
        assertEquals(1,jdbc.queryForObject("select count(*) from audit_event where action='estimate.supersede' and resource=?",Integer.class,task));
        mvc.perform(get(base + "estimate-requests?status=SUPERSEDED").with(as(worker,"lab-a","agent")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].status").value("SUPERSEDED"));
        mvc.perform(post(requests).with(as(worker,"lab-a","agent")).contentType("application/json").content(payload)).andExpect(status().isForbidden());
        for (boolean approved : List.of(true,false))
            mvc.perform(post(base + "estimate-requests/" + request + "/decision").with(as("coordinator","lab-a","coordinator"))
                .contentType("application/json").content("{\"approved\":" + approved + "}")).andExpect(status().isConflict());
        mvc.perform(post(requests).with(as(next,"lab-a","agent")).contentType("application/json").content(payload)).andExpect(status().isCreated());
        assignEstimate(task,worker).andExpect(status().isOk());
        assertEquals(2,jdbc.queryForObject("select count(*) from hpdi_estimate_request where task_id=? and status='SUPERSEDED'",Integer.class,task));
        mvc.perform(post(base + "estimate-requests/" + request + "/decision").with(as("coordinator","lab-a","coordinator"))
            .contentType("application/json").content("{\"approved\":true}")).andExpect(status().isConflict());
        assertEquals(clocks,jdbc.queryForMap("select * from servicedesk_sla where handle_task_id=?",task));
        assertEquals(90,jdbc.queryForObject("select estimate_minutes from servicedesk_review where ticket_id=?::uuid",Integer.class,ticket.path("id").asText()));
    }

    @Test void r2CreationReconcilesPreUpgradeStaleAssignmentWithAudit() throws Exception {
        String worker = "legacy-old-" + UUID.randomUUID(), next = "legacy-new-" + UUID.randomUUID();
        var ticket = create("owner-" + UUID.randomUUID(), "Upgrade stale request", "requester");
        String task = body(review(ticket,"coordinator",worker,90).andExpect(status().isOk())).path("taskId").asText();
        estimateMembers(worker,next);
        String route = "/api/hpdi/v1/workspaces/lab-a/tasks/" + task + "/estimate-requests";
        String payload = "{\"proposedMinutes\":120,\"reason\":\"More work\"}";
        mvc.perform(post(route).with(as(worker,"lab-a","agent")).contentType("application/json").content(payload)).andExpect(status().isCreated());
        jdbc.update("update act_ru_task set assignee_=? where id_=?",next,task);
        jdbc.update("update servicedesk_review set assignee=? where ticket_id=?::uuid",next,ticket.path("id").asText());
        mvc.perform(post(route).with(as(next,"lab-a","agent")).contentType("application/json").content(payload)).andExpect(status().isCreated());
        assertEquals(next,jdbc.queryForObject("select superseded_by from hpdi_estimate_request where task_id=? and status='SUPERSEDED'",String.class,task));
        assertEquals(1,jdbc.queryForObject("select count(*) from audit_event where action='estimate.supersede' and resource=?",Integer.class,task));
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"CREATE_OLD", "CREATE_NEW", "APPROVE"})
    void r2ReassignSerializesWithCreateAndApprove(String operation) throws Exception {
        String worker = "race-old-" + UUID.randomUUID(), next = "race-new-" + UUID.randomUUID();
        var ticket = create("owner-" + UUID.randomUUID(), "Reassign race", "requester");
        String task = body(review(ticket,"coordinator",worker,90).andExpect(status().isOk())).path("taskId").asText();
        estimateMembers(worker,next);
        String base = "/api/hpdi/v1/workspaces/lab-a/";
        String requests = base + "tasks/" + task + "/estimate-requests";
        String payload = "{\"proposedMinutes\":120,\"reason\":\"More work\"}";
        String request = "APPROVE".equals(operation) ? body(mvc.perform(post(requests).with(as(worker,"lab-a","agent"))
            .contentType("application/json").content(payload)).andExpect(status().isCreated())).path("id").asText() : null;
        var pool = Executors.newFixedThreadPool(2);
        try {
            var gate = new CountDownLatch(1);
            var assignment = pool.submit(() -> { gate.await(); return assignEstimate(task,next).andReturn().getResponse().getStatus(); });
            var mutation = pool.submit(() -> {
                gate.await();
                return "APPROVE".equals(operation)
                    ? mvc.perform(post(base + "estimate-requests/" + request + "/decision").with(as("coordinator","lab-a","coordinator"))
                        .contentType("application/json").content("{\"approved\":true}")).andReturn().getResponse().getStatus()
                    : mvc.perform(post(requests).with(as("CREATE_OLD".equals(operation) ? worker : next,"lab-a","agent"))
                        .contentType("application/json").content(payload)).andReturn().getResponse().getStatus();
            });
            gate.countDown();
            assertEquals(200,assignment.get(20,TimeUnit.SECONDS));
            assertTrue(("APPROVE".equals(operation) ? List.of(200,409) : List.of(201,403)).contains(mutation.get(20,TimeUnit.SECONDS)));
        } finally { pool.shutdownNow(); }
        assertEquals(0,jdbc.queryForObject("select count(*) from hpdi_estimate_request where task_id=? and requested_by=? and status='PENDING'",Integer.class,task,worker));
        if (request != null) mvc.perform(post(base + "estimate-requests/" + request + "/decision").with(as("coordinator","lab-a","coordinator"))
            .contentType("application/json").content("{\"approved\":true}")).andExpect(status().isConflict());
        if (jdbc.queryForObject("select count(*) from hpdi_estimate_request where task_id=? and status='PENDING'",Integer.class,task) == 0)
            mvc.perform(post(requests).with(as(next,"lab-a","agent")).contentType("application/json").content(payload)).andExpect(status().isCreated());
        assertEquals(next,jdbc.queryForObject("select requested_by from hpdi_estimate_request where task_id=? and status='PENDING'",String.class,task));
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {false,true})
    void r6WrongGroupRecomputesDefaultButCarriesCoordinatorEstimate(boolean explicit) throws Exception {
        String worker = "category-" + UUID.randomUUID();
        var ticket = create("owner-" + UUID.randomUUID(), "Wrong category", "requester");
        String task = body(review(ticket,"coordinator",worker,explicit ? 110 : null).andExpect(status().isOk())).path("taskId").asText();
        String base = "/api/hpdi/v1/workspaces/lab-a/";
        var clocks = jdbc.queryForMap("select approved_at,start_by,resolve_by from servicedesk_sla where handle_task_id=?",task);
        mvc.perform(post(base + "tasks/" + task + "/release").with(as(worker,"lab-a","agent"))
            .contentType("application/json").content("{\"reason\":\"WRONG_GROUP\",\"note\":\"Account issue\"}")).andExpect(status().isNoContent());
        var changed = body(mvc.perform(post(BASE + "/" + ticket.path("id").asText() + "/review").with(as("coordinator","lab-a","coordinator"))
            .contentType("application/json").content("{\"category\":\"ACCOUNT\",\"priority\":\"HIGH\",\"assignee\":\"" + worker + "\",\"reason\":\"Correct group\"}"))
            .andExpect(status().isOk()));
        int expected = explicit ? 110 : 20;
        assertEquals(expected,changed.path("estimate").path("minutes").asInt());
        assertEquals(explicit ? "COORDINATOR" : "DEFAULT",changed.path("estimate").path("basis").asText());
        String nextTask = changed.path("taskId").asText();
        assertEquals(clocks,jdbc.queryForMap("select approved_at,start_by,resolve_by from servicedesk_sla where handle_task_id=?",nextTask));
        assertEquals(expected,jdbc.queryForObject("select minutes from servicedesk_estimate_history where ticket_id=?::uuid and review_task_id=(select review_task_id from servicedesk_review where ticket_id=?::uuid)",Integer.class,ticket.path("id").asText(),ticket.path("id").asText()));
        assertTrue(jdbc.queryForObject("select count(*) from servicedesk_estimate_history where ticket_id=?::uuid and category='HARDWARE'",Integer.class,ticket.path("id").asText()) > 0);
        var queue = body(mvc.perform(get(base + "queue").with(as(worker,"lab-a","agent"))).andExpect(status().isOk()));
        var item = java.util.stream.StreamSupport.stream(queue.path("items").spliterator(),false)
            .filter(value -> value.path("id").asText().equals(nextTask)).findFirst().orElseThrow();
        assertEquals(expected,item.path("remainingMinutes").asInt());
        assertEquals(expected,queue.path("loadMinutes").asInt());
    }

    private void estimateMembers(String worker, String next) {
        when(directory.members("lab-a")).thenReturn(List.of(
            new vn.haui.dx.core.identity.api.DirectoryMember(worker,worker,null,java.util.Set.of(new vn.haui.dx.core.identity.api.WorkspaceRole("agent"))),
            new vn.haui.dx.core.identity.api.DirectoryMember(next,next,null,java.util.Set.of(new vn.haui.dx.core.identity.api.WorkspaceRole("agent"))),
            new vn.haui.dx.core.identity.api.DirectoryMember("coordinator","coordinator",null,java.util.Set.of(vn.haui.dx.core.identity.api.WorkspaceRole.COORDINATOR))));
    }

    private ResultActions assignEstimate(String task, String worker) throws Exception {
        return mvc.perform(post("/api/hpdi/v1/workspaces/lab-a/tasks/" + task + "/assign").with(as("coordinator","lab-a","coordinator"))
            .contentType("application/json").content("{\"assignee\":\"" + worker + "\"}"));
    }

    @Test void r2ConcurrentRequestsAndDecisionsHaveOneWinner() throws Exception {
        String worker = "race-" + UUID.randomUUID();
        var ticket = create("owner-" + UUID.randomUUID(),"Estimate race", "requester");
        String task = body(review(ticket,"coordinator",worker,90).andExpect(status().isOk())).path("taskId").asText();
        String base = "/api/hpdi/v1/workspaces/lab-a/";
        var pool = Executors.newFixedThreadPool(2);
        try {
            var gate = new CountDownLatch(1);
            java.util.concurrent.Callable<Integer> propose = () -> {
                gate.await();
                return mvc.perform(post(base + "tasks/" + task + "/estimate-requests").with(as(worker,"lab-a","agent"))
                    .contentType("application/json").content("{\"proposedMinutes\":120,\"reason\":\"More work\"}")).andReturn().getResponse().getStatus();
            };
            var first = pool.submit(propose); var second = pool.submit(propose); gate.countDown();
            assertEquals(java.util.Set.of(201,409),java.util.Set.of(first.get(20,TimeUnit.SECONDS),second.get(20,TimeUnit.SECONDS)));
            String request = jdbc.queryForObject("select id::text from hpdi_estimate_request where task_id=?",String.class,task);
            var decisionGate = new CountDownLatch(1);
            java.util.concurrent.Callable<Integer> decide = () -> {
                decisionGate.await();
                return mvc.perform(post(base + "estimate-requests/" + request + "/decision").with(as("coordinator","lab-a","coordinator"))
                    .contentType("application/json").content("{\"approved\":true}")).andReturn().getResponse().getStatus();
            };
            var approved = pool.submit(decide); var duplicate = pool.submit(decide); decisionGate.countDown();
            assertEquals(java.util.Set.of(200,409),java.util.Set.of(approved.get(20,TimeUnit.SECONDS),duplicate.get(20,TimeUnit.SECONDS)));
        } finally { pool.shutdownNow(); }
    }

    @Test void r2AndR6AuditFailuresRollbackAllMutations() throws Exception {
        String worker = "rollback-" + UUID.randomUUID();
        var ticket = create("owner-" + UUID.randomUUID(),"Rollback", "requester");
        String task = body(review(ticket,"coordinator",worker,90).andExpect(status().isOk())).path("taskId").asText();
        String base = "/api/hpdi/v1/workspaces/lab-a/";
        var request = body(mvc.perform(post(base + "tasks/" + task + "/estimate-requests").with(as(worker,"lab-a","agent"))
            .contentType("application/json").content("{\"proposedMinutes\":120,\"reason\":\"More work\"}")).andExpect(status().isCreated()));
        jdbc.execute("create function reject_work_adjustment() returns trigger language plpgsql as $$ begin if new.action in ('estimate.decision','estimate.supersede','task.release') then raise exception 'reject adjustment' using errcode='23514'; end if; return new; end $$");
        jdbc.execute("create trigger reject_work_adjustment before insert on audit_event for each row execute function reject_work_adjustment()");
        try {
            mvc.perform(post(base + "estimate-requests/" + request.path("id").asText() + "/decision").with(as("coordinator","lab-a","coordinator"))
                .contentType("application/json").content("{\"approved\":true}")).andExpect(status().isConflict());
            assertEquals("PENDING", jdbc.queryForObject("select status from hpdi_estimate_request where task_id=?",String.class,task));
            assertEquals(90,jdbc.queryForObject("select estimate_minutes from servicedesk_review where ticket_id=?",Integer.class,UUID.fromString(ticket.path("id").asText())));
            mvc.perform(post(base + "tasks/" + task + "/release").with(as(worker,"lab-a","agent")).contentType("application/json")
                .content("{\"reason\":\"HANDOVER\",\"note\":\"Continue diagnostics\"}")).andExpect(status().isConflict());
            assertEquals(1,jdbc.queryForObject("select count(*) from act_ru_task where id_=?",Integer.class,task));
            assertEquals(0,jdbc.queryForObject("select count(*) from servicedesk_work_release where task_id=?",Integer.class,task));
            assertNull(jdbc.queryForObject("select released_at from servicedesk_sla where handle_task_id=?",java.sql.Timestamp.class,task));
            String next = "rollback-next-" + UUID.randomUUID();
            estimateMembers(worker,next);
            assignEstimate(task,next).andExpect(status().isConflict());
            assertEquals(worker,jdbc.queryForObject("select assignee_ from act_ru_task where id_=?",String.class,task));
            assertEquals("PENDING",jdbc.queryForObject("select status from hpdi_estimate_request where task_id=?",String.class,task));
            assertNull(jdbc.queryForObject("select superseded_at from hpdi_estimate_request where task_id=?",java.sql.Timestamp.class,task));
        } finally {
            jdbc.execute("drop trigger reject_work_adjustment on audit_event");
            jdbc.execute("drop function reject_work_adjustment()");
        }
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"{}", "{\"proposedMinutes\":0,\"reason\":\"x\"}",
        "{\"proposedMinutes\":10081,\"reason\":\"x\"}", "{\"proposedMinutes\":1.2,\"reason\":\"x\"}",
        "{\"proposedMinutes\":\"120\",\"reason\":\"x\"}", "{\"proposedMinutes\":120,\"reason\":\"   \"}",
        "{\"proposedMinutes\":120,\"reason\":1}", "{\"proposedMinutes\":120,\"reason\":\"x\",\"extra\":true}"})
    void r2RejectsInvalidJsonWithoutServerErrors(String payload) throws Exception {
        mvc.perform(post("/api/hpdi/v1/workspaces/lab-a/tasks/unknown/estimate-requests").with(as("agent","lab-a","agent"))
            .contentType("application/json").content(payload)).andExpect(status().isBadRequest());
    }

    @Test void r6ReleaseAuthorizationAndDuplicateFailClosed() throws Exception {
        String worker = "secured-" + UUID.randomUUID();
        var ticket = create("owner-" + UUID.randomUUID(),"Release permissions", "requester");
        String task = body(review(ticket,"coordinator",worker,90).andExpect(status().isOk())).path("taskId").asText();
        String route = "/api/hpdi/v1/workspaces/lab-a/tasks/" + task + "/release";
        String input = "{\"reason\":\"HANDOVER\",\"note\":\"Checked hardware\"}";
        for (String role : List.of("agent","requester")) mvc.perform(post(route).with(as("other","lab-a",role))
            .contentType("application/json").content(input)).andExpect(status().isForbidden());
        mvc.perform(post(route.replace("lab-a","lab-b")).with(as(worker,"lab-b","agent"))
            .contentType("application/json").content(input)).andExpect(status().isNotFound());
        mvc.perform(post(route).with(as(worker,"lab-a","agent")).contentType("application/json")
            .content("{\"reason\":\"HANDOVER\",\"note\":123}")).andExpect(status().isBadRequest());
        mvc.perform(post(route).with(as(worker,"lab-a","agent")).contentType("application/json").content(input)).andExpect(status().isNoContent());
        mvc.perform(post(route).with(as(worker,"lab-a","agent")).contentType("application/json").content(input)).andExpect(status().isConflict());
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"release", "complete"})
    void r2DecisionRacesWithTaskTermination(String operation) throws Exception {
        String worker = "race-end-" + UUID.randomUUID();
        var ticket = create("owner-" + UUID.randomUUID(),"Concurrent termination", "requester");
        String task = body(review(ticket,"coordinator",worker,90).andExpect(status().isOk())).path("taskId").asText();
        String base = "/api/hpdi/v1/workspaces/lab-a/";
        mvc.perform(post(base + "tasks/" + task + "/start").with(as(worker,"lab-a","agent"))).andExpect(status().isOk());
        var request = body(mvc.perform(post(base + "tasks/" + task + "/estimate-requests").with(as(worker,"lab-a","agent"))
            .contentType("application/json").content("{\"proposedMinutes\":120,\"reason\":\"More work\"}")).andExpect(status().isCreated()));
        var pool = Executors.newFixedThreadPool(2);
        try {
            var gate = new CountDownLatch(1);
            var decision = pool.submit(() -> {
                gate.await();
                return mvc.perform(post(base + "estimate-requests/" + request.path("id").asText() + "/decision").with(as("coordinator","lab-a","coordinator"))
                    .contentType("application/json").content("{\"approved\":true}")).andReturn().getResponse().getStatus();
            });
            var terminal = pool.submit(() -> {
                gate.await();
                String input = "release".equals(operation) ? "{\"reason\":\"HANDOVER\",\"note\":\"Continue\"}"
                    : "{\"expectedTaskKey\":\"handle\",\"outcome\":\"resolved\"}";
                return mvc.perform(post(base + "tasks/" + task + "/" + operation).with(as(worker,"lab-a","agent"))
                    .contentType("application/json").content(input)).andReturn().getResponse().getStatus();
            });
            gate.countDown();
            int outcome = decision.get(20,TimeUnit.SECONDS);
            assertTrue(outcome == 200 || outcome == 409);
            assertEquals(204,terminal.get(20,TimeUnit.SECONDS));
            assertEquals(outcome == 200 ? "APPROVED" : "PENDING",jdbc.queryForObject("select status from hpdi_estimate_request where task_id=?",String.class,task));
            assertEquals(0,jdbc.queryForObject("select count(*) from act_ru_task where id_=?",Integer.class,task));
        } finally { pool.shutdownNow(); }
    }

    @Test void r2RevokedMembershipAndOffDutyBlockDecisions() throws Exception {
        String worker = "revoked-" + UUID.randomUUID();
        var ticket = create("owner-" + UUID.randomUUID(),"Revocation", "requester");
        String task = body(review(ticket,"coordinator",worker,90).andExpect(status().isOk())).path("taskId").asText();
        String base = "/api/hpdi/v1/workspaces/lab-a/";
        var request = body(mvc.perform(post(base + "tasks/" + task + "/estimate-requests").with(as(worker,"lab-a","agent"))
            .contentType("application/json").content("{\"proposedMinutes\":120,\"reason\":\"More work\"}")).andExpect(status().isCreated()));
        doReturn(false).when(duty).onDuty("lab-a",worker);
        doReturn(false).when(duty).manualDuty("lab-a",worker);
        String route = base + "estimate-requests/" + request.path("id").asText() + "/decision";
        mvc.perform(post(route).with(as("coordinator","lab-a","coordinator")).contentType("application/json").content("{\"approved\":true}"))
            .andExpect(status().isConflict());
        doReturn(true).when(duty).onDuty("lab-a",worker);
        doReturn(true).when(duty).manualDuty("lab-a",worker);
        when(directory.members("lab-a")).thenReturn(List.of());
        mvc.perform(post(route).with(as("coordinator","lab-a","coordinator")).contentType("application/json").content("{\"approved\":true}"))
            .andExpect(status().isForbidden());
        assertEquals("PENDING",jdbc.queryForObject("select status from hpdi_estimate_request where task_id=?",String.class,task));
    }

    @Test void r6ConcurrentReleaseCreatesExactlyOneReview() throws Exception {
        String worker = "release-race-" + UUID.randomUUID();
        var ticket = create("owner-" + UUID.randomUUID(),"Concurrent handover", "requester");
        String task = body(review(ticket,"coordinator",worker,90).andExpect(status().isOk())).path("taskId").asText();
        var pool = Executors.newFixedThreadPool(2);
        try {
            var gate = new CountDownLatch(1);
            java.util.concurrent.Callable<Integer> release = () -> {
                gate.await();
                return mvc.perform(post("/api/hpdi/v1/workspaces/lab-a/tasks/" + task + "/release").with(as(worker,"lab-a","agent"))
                    .contentType("application/json").content("{\"reason\":\"HANDOVER\",\"note\":\"Continue\"}")).andReturn().getResponse().getStatus();
            };
            var first = pool.submit(release); var second = pool.submit(release); gate.countDown();
            assertEquals(java.util.Set.of(204,409),java.util.Set.of(first.get(20,TimeUnit.SECONDS),second.get(20,TimeUnit.SECONDS)));
            assertEquals(1,jdbc.queryForObject("select count(*) from servicedesk_work_release where task_id=?",Integer.class,task));
            assertEquals(1,jdbc.queryForObject("select count(*) from act_ru_task where proc_inst_id_=? and task_def_key_='review'",Integer.class,ticket.path("process").path("processInstanceId").asText()));
        } finally { pool.shutdownNow(); }
    }

    @Test void r7ApprovedCriticalPersistsAlertWithoutStartingOrInterruptingWork() throws Exception {
        var ticket = create("critical-owner", "Critical reviewed", "requester");
        var active = body(review(ticket, "critical-coordinator", "critical-agent", null).andExpect(status().isOk()));
        mvc.perform(post("/api/hpdi/v1/workspaces/lab-a/tasks/" + active.path("taskId").asText() + "/start").with(as("critical-agent", "lab-a", "agent")))
            .andExpect(status().isOk());
        var other = create("critical-other-owner", "Independent critical", "requester");
        var reviewed = body(mvc.perform(post(BASE + "/" + other.path("id").asText() + "/review").with(as("critical-coordinator", "lab-a", "coordinator"))
            .contentType("application/json").content("{\"category\":\"HARDWARE\",\"priority\":\"CRITICAL\",\"assignee\":\"critical-agent\",\"reason\":\"Urgent human decision\"}")).andExpect(status().isOk()));
        UUID id = UUID.fromString(other.path("id").asText());
        assertNull(slaService.read("lab-a", id).firstStarted());
        assertEquals("ASSIGNED", reviewed.path("state").asText());
        assertEquals(1, jdbc.queryForObject("select count(*) from hpdi_alert_event where business_key=? and type='CRITICAL_NEW' and agent='critical-agent'", Integer.class, id.toString()));
        mvc.perform(get(BASE + "/" + ticket.path("id").asText()).with(as("critical-agent", "lab-a", "agent")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.state").value("IN_PROGRESS"));
    }

    @Test void r8ConcurrentEvaluationDeduplicatesAndReviewRoundIsolationIsPreserved() throws Exception {
        var ticket = create("alert-sla-owner", "Alert SLA", "requester");
        var reviewed = body(review(ticket, "alert-coordinator", null, null).andExpect(status().isOk()));
        UUID id = UUID.fromString(ticket.path("id").asText());
        var state = slaService.read("lab-a", id);
        jdbc.update("update servicedesk_sla set approved_at=approved_at-interval '10 days',start_by=start_by-interval '10 days',resolve_by=resolve_by-interval '10 days' where ticket_id=?", id);
        var pool = Executors.newFixedThreadPool(2);
        try {
            var first = pool.submit(() -> ticketAlerts.check("lab-a", state.processId(), state.taskId(), null));
            var second = pool.submit(() -> ticketAlerts.check("lab-a", state.processId(), state.taskId(), null));
            first.get(20, TimeUnit.SECONDS); second.get(20, TimeUnit.SECONDS);
        } finally { pool.shutdownNow(); }
        assertEquals(2, jdbc.queryForObject("select count(*) from hpdi_alert_event where business_key=?", Integer.class, id.toString()));
        assertNull(slaService.read("lab-a", id).firstStarted());
        assertEquals(reviewed.path("taskId").asText(), state.taskId());
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(longs = {299, 301})
    void r7AdvisoryNeverApprovesOrQueuesBeforeOrAfterFiveMinutes(long seconds) throws Exception {
        var created = create("advisory-owner", "Advisory critical", "requester");
        UUID id = UUID.fromString(created.path("id").asText());
        var submitted = java.time.Instant.now().minusSeconds(seconds);
        jdbc.update("update servicedesk_ticket set created_at=? where id=?", java.sql.Timestamp.from(submitted), id);
        var stored = new Ticket(id, "lab-a", "advisory-owner", UUID.randomUUID(), "Synthetic", "Synthetic", submitted,
            new TicketProcessView(created.path("process").path("processInstanceId").asText(), ServiceDeskProcess.REVIEWED_KEY, id.toString(), "lab-a"));
        var transaction = new org.springframework.transaction.support.TransactionTemplate(new org.springframework.jdbc.datasource.DataSourceTransactionManager(jdbc.getDataSource()));
        transaction.executeWithoutResult(status -> ticketAlerts.advisory(stored, "assessment-1", "CRITICAL"));
        transaction.executeWithoutResult(status -> ticketAlerts.advisory(stored, "assessment-1", "CRITICAL"));
        assertEquals(1, jdbc.queryForObject("select count(*) from hpdi_alert_event where business_key=? and label like '%UNAPPROVED%'", Integer.class, id.toString()));
        assertEquals(0, jdbc.queryForObject("select count(*) from servicedesk_review where ticket_id=?", Integer.class, id));
        assertNull(slaService.read("lab-a", id));
        assertEquals("AWAITING_REVIEW", created.path("state").asText());
        mvc.perform(get("/api/hpdi/v1/workspaces/lab-a/instances/" + stored.process().processInstanceId() + "/timeline").with(as("advisory-owner", "lab-a", "requester")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.currentTasks.length()").value(1)).andExpect(jsonPath("$.currentTasks[0].key").value("review"));
        review(created, "advisory-coordinator", null, null).andExpect(status().isOk());
        transaction.executeWithoutResult(status -> ticketAlerts.advisory(stored, "stale-assessment", "CRITICAL"));
        transaction.executeWithoutResult(status -> ticketAlerts.advisory(stored, "abstain", "ABSTAIN"));
        assertEquals(1, jdbc.queryForObject("select count(*) from hpdi_alert_event where business_key=? and type='CRITICAL_NEW'", Integer.class, id.toString()));
    }

    @Test void r7AlertAuditFailureRollsBackReviewAdmissionSlaAndHandleCreation() throws Exception {
        var created = create("critical-rollback-owner", "Critical rollback", "requester");
        UUID id = UUID.fromString(created.path("id").asText());
        jdbc.execute("create function reject_critical_alert() returns trigger language plpgsql as $$ begin if new.action='human.alert.created' then raise exception 'reject alert' using errcode='23514'; end if; return new; end $$");
        jdbc.execute("create trigger reject_critical_alert before insert on audit_event for each row execute function reject_critical_alert()");
        try {
            mvc.perform(post(BASE + "/" + id + "/review").with(as("critical-coordinator", "lab-a", "coordinator"))
                .contentType("application/json").content("{\"category\":\"HARDWARE\",\"priority\":\"CRITICAL\",\"assignee\":null,\"reason\":\"Urgent human decision\"}"))
                .andExpect(status().isConflict());
            assertEquals(0, jdbc.queryForObject("select count(*) from servicedesk_review where ticket_id=?", Integer.class, id));
            assertEquals(0, jdbc.queryForObject("select count(*) from hpdi_alert_event where business_key=?", Integer.class, id.toString()));
            assertNull(slaService.read("lab-a", id));
            mvc.perform(get(BASE + "/" + id).with(as("critical-rollback-owner", "lab-a", "requester")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.state").value("AWAITING_REVIEW"));
        } finally { jdbc.execute("drop trigger reject_critical_alert on audit_event"); jdbc.execute("drop function reject_critical_alert()"); }
    }

    @Test void slaApprovalSnapshotsCoordinatorDecisionAndLeavesUnreviewedAndForeignTicketsWithoutSla() throws Exception {
        var ticket = create("sla-owner", "SLA approval", "requester");
        assertTrue(ticket.path("sla").isNull());
        var reviewed = body(review(ticket, "sla-coordinator", null, null).andExpect(status().isOk()));
        assertEquals("HIGH", reviewed.path("classification").path("basePriority").asText());
        assertFalse(reviewed.path("sla").path("startBreached").asBoolean());
        assertFalse(reviewed.path("sla").path("resolveBreached").asBoolean());
        var id = UUID.fromString(ticket.path("id").asText());
        var state = slaService.read("lab-a", id);
        // Bản chụp chính sách theo version Cấu hình hệ thống lúc duyệt
        assertTrue(state.policy().version().startsWith("r5-settings-v"));
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
        doReturn(true).when(duty).manualDuty(anyString(), anyString());
        doAnswer(invocation ->
            directory.members(invocation.getArgument(0)).stream().map(vn.haui.dx.core.identity.api.DirectoryMember::sub)
                .collect(java.util.stream.Collectors.toSet())).when(directory).supportGroupMembers(anyString(), anyString());
    }

    @Test void r9AuditFailureRollsBackHistoryEtSlaAlertsAndNativeReturn() throws Exception {
        var ticket = create("r9-rollback-owner-"+UUID.randomUUID(),"R9 rollback","requester");
        var reviewed = body(review(ticket,"coordinator","r9-rollback-agent",75).andExpect(status().isOk()));
        String task = reviewed.path("taskId").asText();
        UUID id = UUID.fromString(ticket.path("id").asText());
        var before = jdbc.queryForMap("select * from servicedesk_sla where handle_task_id=?",task);
        var oldReview = jdbc.queryForMap("select * from servicedesk_review where ticket_id=?",id);
        jdbc.execute("create function r9_reject_audit() returns trigger language plpgsql as $$ begin if new.action='servicedesk.ticket.classification.correct' then raise exception 'R9 audit rejection' using errcode='23514'; end if; return new; end $$");
        jdbc.execute("create trigger r9_reject before insert on audit_event for each row execute function r9_reject_audit()");
        try {
            mvc.perform(patch(BASE+"/"+id+"/classification").with(as("coordinator","lab-a","coordinator"))
                .contentType("application/json").content("{\"category\":\"NETWORK\",\"priority\":\"CRITICAL\",\"reason\":\"Verified\"}"))
                .andExpect(status().isConflict());
            assertEquals(before,jdbc.queryForMap("select * from servicedesk_sla where handle_task_id=?",task));
            assertEquals(oldReview,jdbc.queryForMap("select * from servicedesk_review where ticket_id=?",id));
            assertEquals(1,jdbc.queryForObject("select count(*) from act_ru_task where id_=?",Integer.class,task));
            for (var table : List.of("servicedesk_classification_correction","servicedesk_sla_amendment","servicedesk_work_release"))
                assertEquals(0,jdbc.queryForObject("select count(*) from "+table+" where ticket_id=?",Integer.class,id));
            assertEquals(0,jdbc.queryForObject("select count(*) from hpdi_alert_event where task_id=? and type='CLASSIFICATION_CHANGED'",Integer.class,task));
        } finally {
            jdbc.execute("drop trigger r9_reject on audit_event");
            jdbc.execute("drop function r9_reject_audit()");
        }
    }

    @Test void r9PausedPriorityChangesKeepManualEstimateAndPersistBreachAcrossRestart() throws Exception {
        String worker = "r9-paused-"+UUID.randomUUID();
        var ticket = create("r9-paused-owner-"+UUID.randomUUID(),"R9 paused priority","requester");
        var reviewed = body(review(ticket,"coordinator",worker,75).andExpect(status().isOk()));
        String task = reviewed.path("taskId").asText();
        UUID id = UUID.fromString(ticket.path("id").asText());
        String actions = "/api/hpdi/v1/workspaces/lab-a/tasks/"+task;
        mvc.perform(post(actions+"/start").with(as(worker,"lab-a","agent"))).andExpect(status().isOk());
        mvc.perform(post(actions+"/pause").with(as(worker,"lab-a","agent")).contentType("application/json")
            .content("{\"reason\":\"WAITING_REQUESTER\",\"note\":\"Need evidence\"}")).andExpect(status().isOk());
        var before = jdbc.queryForMap("select * from servicedesk_sla where handle_task_id=?",task);
        String route = BASE+"/"+id+"/classification";
        var corrected = body(mvc.perform(patch(route).with(as(worker,"lab-a","agent")).contentType("application/json")
            .content("{\"priority\":\"CRITICAL\",\"reason\":\"Confirmed urgent impact\"}")).andExpect(status().isOk()));
        assertEquals("PENDING",corrected.path("workState").asText());
        assertEquals(75,corrected.path("estimate").path("minutes").asInt());
        assertTrue(corrected.path("sla").path("clockPaused").asBoolean());
        var paused = jdbc.queryForMap("select * from servicedesk_sla where handle_task_id=?",task);
        for (var field : List.of("approved_at","first_started_at","paused_at","policy","base_priority")) assertEquals(before.get(field),paused.get(field));
        mvc.perform(post(actions+"/resume").with(as(worker,"lab-a","agent"))).andExpect(status().isOk());
        jdbc.update("update servicedesk_sla set approved_at=approved_at-interval '30 days',start_by=start_by-interval '30 days',"
            + "resolve_by=resolve_by-interval '30 days' where handle_task_id=?",task);
        corrected = body(mvc.perform(patch(route).with(as(worker,"lab-a","agent")).contentType("application/json")
            .content("{\"priority\":\"LOW\",\"reason\":\"Mitigated impact; preserve old breach\"}")).andExpect(status().isOk()));
        assertTrue(corrected.path("sla").path("resolveBreached").asBoolean());
        var state = slaService.read("lab-a",id);
        var restarted = new TicketSlaService(jdbc,json,state.policy(),java.time.Clock.systemUTC(),
            mock(vn.haui.dx.hpdi.process.api.WorkScheduling.class),mock(vn.haui.dx.hpdi.human.api.ActivityJournal.class));
        assertEquals(state,restarted.read("lab-a",id));
        slaService.reconcile("lab-a",state.processId(),task);
        assertEquals("LOW",jdbc.queryForObject("select priority from servicedesk_sla where handle_task_id=?",String.class,task));
        assertEquals(2,jdbc.queryForObject("select count(*) from servicedesk_sla_amendment where ticket_id=?",Integer.class,id));
    }

    @Test void r9IncumbentEtExceptionDoesNotWeakenOrdinaryR2GroupChecks() throws Exception {
        String worker = "r9-incumbent-"+UUID.randomUUID();
        var ticket = create("r9-incumbent-owner-"+UUID.randomUUID(),"R9 retained incumbent","requester");
        var reviewed = body(review(ticket,"coordinator",worker,null).andExpect(status().isOk()));
        String task = reviewed.path("taskId").asText();
        String actions = "/api/hpdi/v1/workspaces/lab-a/tasks/"+task;
        mvc.perform(post(actions+"/start").with(as(worker,"lab-a","agent"))).andExpect(status().isOk());
        doReturn(java.util.Set.of()).when(directory).supportGroupMembers(eq("lab-a"),anyString());
        String estimate = "{\"proposedMinutes\":120,\"reason\":\"Changed diagnostics\"}";
        mvc.perform(post(actions+"/estimate-requests").with(as(worker,"lab-a","agent")).contentType("application/json")
            .content(estimate)).andExpect(status().isBadRequest());
        mvc.perform(patch(BASE+"/"+ticket.path("id").asText()+"/classification").with(as("coordinator","lab-a","coordinator"))
            .contentType("application/json").content("{\"category\":\"NETWORK\",\"reason\":\"Retain current worker\"}"))
            .andExpect(status().isOk());
        mvc.perform(post(actions+"/estimate-requests").with(as(worker,"lab-a","agent")).contentType("application/json")
            .content(estimate)).andExpect(status().isCreated());
        String next = "r9-replacement-"+UUID.randomUUID();
        var members = new java.util.ArrayList<>(directory.members("lab-a"));
        members.add(new vn.haui.dx.core.identity.api.DirectoryMember(next,next,null,java.util.Set.of(vn.haui.dx.core.identity.api.WorkspaceRole.AGENT)));
        when(directory.members("lab-a")).thenReturn(members);
        doReturn(java.util.Set.of(next)).when(directory).supportGroupMembers("lab-a","network");
        mvc.perform(post(actions+"/pause").with(as(worker,"lab-a","agent")).contentType("application/json")
            .content("{\"reason\":\"WAITING_EXTERNAL\"}")).andExpect(status().isOk());
        mvc.perform(post(actions+"/assign").with(as("coordinator","lab-a","coordinator")).contentType("application/json")
            .content("{\"assignee\":\""+next+"\"}")).andExpect(status().isOk());
        assertNull(jdbc.queryForObject("select correction_incumbent from servicedesk_review where ticket_id=?",String.class,
            UUID.fromString(ticket.path("id").asText())));
    }

    @Test void r9RejectsUnreviewedRequesterForeignWorkspaceMissingTicketAndUnauthenticatedCalls() throws Exception {
        String owner = "r9-security-owner-"+UUID.randomUUID();
        var ticket = create(owner,"R9 security","requester");
        String route = BASE+"/"+ticket.path("id").asText()+"/classification";
        String payload = "{\"priority\":\"CRITICAL\",\"reason\":\"Verified impact\"}";
        mvc.perform(patch(route).contentType("application/json").content(payload)).andExpect(status().isUnauthorized());
        mvc.perform(patch(route).with(as("coordinator","lab-b","coordinator")).contentType("application/json").content(payload)).andExpect(status().isForbidden());
        mvc.perform(patch(route).with(as("coordinator","lab-a","coordinator")).contentType("application/json").content(payload)).andExpect(status().isConflict());
        mvc.perform(patch(BASE+"/"+UUID.randomUUID()+"/classification").with(as("coordinator","lab-a","coordinator"))
            .contentType("application/json").content(payload)).andExpect(status().isNotFound());
        review(ticket,"coordinator","r9-security-agent",null).andExpect(status().isOk());
        mvc.perform(patch(route).with(as(owner,"lab-a","requester")).contentType("application/json").content(payload)).andExpect(status().isForbidden());
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings={"START","PAUSE","RESUME","RELEASE","COMPLETE","REASSIGN","ET_APPROVAL","SLA_WORKER","CORRECTION"})
    void r9ConcurrentMutationSerializesAtProcessScope(String competing) throws Exception {
        String worker = "r9-race-"+UUID.randomUUID();
        String replacement = "r9-race-next-"+UUID.randomUUID();
        var ticket = create("r9-race-owner-"+UUID.randomUUID(),"R9 race","requester");
        var reviewed = body(review(ticket,"coordinator",worker,75).andExpect(status().isOk()));
        if ("REASSIGN".equals(competing)) {
            var members = new java.util.ArrayList<>(directory.members("lab-a"));
            members.add(new vn.haui.dx.core.identity.api.DirectoryMember(replacement,replacement,null,java.util.Set.of(vn.haui.dx.core.identity.api.WorkspaceRole.AGENT)));
            when(directory.members("lab-a")).thenReturn(members);
        }
        String task = reviewed.path("taskId").asText();
        String actions = "/api/hpdi/v1/workspaces/lab-a/tasks/"+task;
        if (List.of("PAUSE","RESUME","COMPLETE").contains(competing))
            mvc.perform(post(actions+"/start").with(as(worker,"lab-a","agent"))).andExpect(status().isOk());
        if ("RESUME".equals(competing)) mvc.perform(post(actions+"/pause").with(as(worker,"lab-a","agent"))
            .contentType("application/json").content("{\"reason\":\"WAITING_REQUESTER\"}")).andExpect(status().isOk());
        String estimateRequest;
        if ("ET_APPROVAL".equals(competing)) estimateRequest = body(mvc.perform(post(actions+"/estimate-requests")
            .with(as(worker,"lab-a","agent")).contentType("application/json")
            .content("{\"proposedMinutes\":120,\"reason\":\"More work\"}")).andExpect(status().isCreated())).path("id").asText();
        else estimateRequest = null;
        String route = BASE+"/"+ticket.path("id").asText()+"/classification";
        var correction = patch(route).with(as("coordinator","lab-a","coordinator")).contentType("application/json")
            .content("{\"category\":\"NETWORK\",\"priority\":\"CRITICAL\",\"reason\":\"Verified classification\"}");
        var competingRequest = switch (competing) {
            case "CORRECTION" -> patch(route).with(as("coordinator","lab-a","coordinator")).contentType("application/json")
                .content("{\"category\":\"NETWORK\",\"priority\":\"CRITICAL\",\"reason\":\"Same correction\"}");
            case "RELEASE" -> post(actions+"/release").with(as(worker,"lab-a","agent")).contentType("application/json")
                .content("{\"reason\":\"HANDOVER\",\"note\":\"Hand over diagnostics\"}");
            case "COMPLETE" -> post(actions+"/complete").with(as(worker,"lab-a","agent")).contentType("application/json")
                .content("{\"expectedTaskKey\":\"handle\",\"outcome\":\"resolved\"}");
            case "REASSIGN" -> post(actions+"/assign").with(as("coordinator","lab-a","coordinator")).contentType("application/json")
                .content("{\"assignee\":\""+replacement+"\"}");
            case "ET_APPROVAL" -> post("/api/hpdi/v1/workspaces/lab-a/estimate-requests/"+estimateRequest+"/decision")
                .with(as("coordinator","lab-a","coordinator")).contentType("application/json").content("{\"approved\":true}");
            case "PAUSE" -> post(actions+"/pause").with(as(worker,"lab-a","agent")).contentType("application/json")
                .content("{\"reason\":\"WAITING_REQUESTER\"}");
            default -> post(actions+"/"+competing.toLowerCase()).with(as(worker,"lab-a","agent"));
        };
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(2);
        try {
            var first = pool.submit(() -> { ready.countDown(); assertTrue(start.await(15,TimeUnit.SECONDS));
                return mvc.perform(correction).andReturn().getResponse().getStatus(); });
            var second = pool.submit(() -> { ready.countDown(); assertTrue(start.await(15,TimeUnit.SECONDS));
                if ("SLA_WORKER".equals(competing)) {
                    slaService.reconcile("lab-a",ticket.path("process").path("processInstanceId").asText(),task); return 200;
                }
                return mvc.perform(competingRequest).andReturn().getResponse().getStatus(); });
            assertTrue(ready.await(15,TimeUnit.SECONDS)); start.countDown();
            var results = List.of(first.get(30,TimeUnit.SECONDS),second.get(30,TimeUnit.SECONDS));
            assertTrue(results.stream().allMatch(code -> List.of(200,204,409).contains(code)),results.toString());
            assertTrue(results.stream().anyMatch(code -> code==200 || code==204),results.toString());
            String process = ticket.path("process").path("processInstanceId").asText();
            assertEquals(1,jdbc.queryForObject("select count(*) from act_ru_task where proc_inst_id_=?",Integer.class,process));
            assertTrue(jdbc.queryForObject("select count(distinct operation_id) from servicedesk_classification_correction where ticket_id=?",
                Integer.class,UUID.fromString(ticket.path("id").asText()))<=1);
            assertEquals(jdbc.queryForObject("select count(distinct operation_id) from servicedesk_classification_correction where ticket_id=?",
                Integer.class,UUID.fromString(ticket.path("id").asText())),jdbc.queryForObject(
                "select count(*) from hpdi_alert_event where task_id=? and type='CLASSIFICATION_CHANGED'",Integer.class,task));
        } finally { start.countDown(); pool.shutdownNow(); }
    }

    @Test void ticketListFiltersByStatePriorityCategoryAssigneeAndSortsLikeReviewQueue() throws Exception {
        String worker = "filter-agent-" + UUID.randomUUID(), owner = "filter-owner-" + UUID.randomUUID();
        String marker = "Filter " + UUID.randomUUID();
        var waiting = create(owner, marker + " waiting", "requester");
        var assigned = create(owner, marker + " assigned", "requester");
        var critical = create(owner, marker + " critical", "requester");
        // Việc ưu tiên thấp đang làm vẫn đứng trước việc khẩn cấp chưa bắt đầu (thứ tự hàng đợi R4)
        String task = body(review(assigned, "review-coordinator", worker, null, "NETWORK", "LOW")
            .andExpect(status().isOk())).path("taskId").asText();
        review(critical, "review-coordinator", worker, null, "HARDWARE", "CRITICAL").andExpect(status().isOk());
        mvc.perform(post("/api/hpdi/v1/workspaces/lab-a/tasks/" + task + "/start").with(as(worker, "lab-a", "agent")))
            .andExpect(status().isOk());
        var coordinator = as("review-coordinator", "lab-a", "coordinator");
        mvc.perform(get(BASE).param("q", marker).param("state", "ASSIGNED").param("state", "IN_PROGRESS").with(coordinator))
            .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(2));
        mvc.perform(get(BASE).param("q", marker).param("assignee", "me").with(as(worker, "lab-a", "agent")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(2));
        mvc.perform(get(BASE).param("q", marker).param("priority", "CRITICAL").with(coordinator))
            .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(1))
            .andExpect(jsonPath("$.items[0].id").value(critical.path("id").asText()))
            .andExpect(jsonPath("$.items[0].state").value("ASSIGNED"));
        mvc.perform(get(BASE).param("q", marker).param("category", "NETWORK").with(coordinator))
            .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(1))
            .andExpect(jsonPath("$.items[0].id").value(assigned.path("id").asText()));
        mvc.perform(get(BASE).param("q", marker).param("sort", "priority").with(coordinator))
            .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(3))
            .andExpect(jsonPath("$.items[0].id").value(assigned.path("id").asText()))
            .andExpect(jsonPath("$.items[0].state").value("IN_PROGRESS"))
            .andExpect(jsonPath("$.items[1].id").value(critical.path("id").asText()))
            .andExpect(jsonPath("$.items[2].id").value(waiting.path("id").asText()));
        // Danh sách không lọc cũng trả trạng thái; người yêu cầu chỉ thấy ticket của mình
        mvc.perform(get(BASE).param("q", marker).with(as(owner, "lab-a", "requester")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(3)).andExpect(jsonPath("$.items[0].state").exists());
        mvc.perform(get(BASE).param("q", marker).param("state", "AWAITING_REVIEW").param("state", "ASSIGNED")
                .with(as("filter-stranger", "lab-a", "requester")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(0));
        for (var invalid : List.of(Map.of("state", "BOGUS"), Map.of("priority", "URGENT"), Map.of("category", "network"),
                Map.of("sort", "title")))
            mvc.perform(get(BASE).param(invalid.keySet().iterator().next(), invalid.values().iterator().next()).with(coordinator))
                .andExpect(status().isBadRequest());
    }

    @Test void commentsFollowTicketVisibilityAndNotifyParticipants() throws Exception {
        String owner = "comment-owner-" + UUID.randomUUID(), other = "comment-other-" + UUID.randomUUID();
        var ticket = create(owner, "Comment " + UUID.randomUUID(), "requester");
        String path = BASE + "/" + ticket.path("id").asText() + "/comments";
        mvc.perform(post(path).with(as(owner, "lab-a", "requester")).contentType("application/json")
            .content("{\"body\":\"  Máy vẫn lỗi\nsau khi khởi động lại  \"}"))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.author").value(owner))
            .andExpect(jsonPath("$.body").value("Máy vẫn lỗi
sau khi khởi động lại"));
        mvc.perform(post(path).with(as("review-coordinator", "lab-a", "coordinator")).contentType("application/json")
            .content("{\"body\":\"Đã nhận\"}")).andExpect(status().isCreated());
        mvc.perform(get(path).with(as(owner, "lab-a", "requester"))).andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(2)).andExpect(jsonPath("$[1].author").value("review-coordinator"));
        // Người gửi khác không thấy ticket nên cũng không đọc/viết được bình luận
        mvc.perform(get(path).with(as(other, "lab-a", "requester"))).andExpect(status().isNotFound());
        mvc.perform(post(path).with(as(other, "lab-a", "requester")).contentType("application/json")
            .content("{\"body\":\"x\"}")).andExpect(status().isNotFound());
        mvc.perform(post(path).with(as(owner, "lab-a", "requester")).contentType("application/json")
            .content("{\"body\":\"   \"}")).andExpect(status().isBadRequest());
        mvc.perform(post(path).with(as(owner, "lab-a", "requester")).contentType("application/json")
            .content("{\"body\":\"" + "a".repeat(2001) + "\"}")).andExpect(status().isBadRequest());
        assertEquals(1, jdbc.queryForObject("select count(*) from hpdi_notification n join hpdi_notification_recipient r"
            + " on r.notification_id=n.id where n.type='TICKET_COMMENTED' and n.business_key=? and r.subject=?",
            Integer.class, ticket.path("id").asText(), owner));
    }

    private ResultActions review(JsonNode ticket, String reviewer, String assignee, Integer minutes) throws Exception {
        return review(ticket,reviewer,assignee,minutes,"HARDWARE","HIGH");
    }

    private ResultActions review(JsonNode ticket, String reviewer, String assignee, Integer minutes, String category, String priority) throws Exception {
        when(directory.members("lab-a")).thenReturn("unknown-agent".equals(assignee) ? List.of() : List.of(new vn.haui.dx.core.identity.api.DirectoryMember(
            Objects.toString(assignee,"review-agent"), Objects.toString(assignee,"review-agent"), null, java.util.Set.of(new vn.haui.dx.core.identity.api.WorkspaceRole("agent"))),
            new vn.haui.dx.core.identity.api.DirectoryMember(reviewer,reviewer,null,java.util.Set.of(vn.haui.dx.core.identity.api.WorkspaceRole.COORDINATOR))));
        var input = json.createObjectNode().put("category",category).put("priority",priority).put("reason","Reviewed by coordinator");
        if (assignee == null) input.putNull("assignee"); else input.put("assignee",assignee);
        if (minutes != null) input.put("estimateMinutes",minutes);
        return mvc.perform(post(BASE+"/"+ticket.path("id").asText()+"/review").with(as(reviewer,"lab-a","coordinator"))
            .contentType("application/json").content(input.toString()));
    }

    @Test void dutyIsRecheckedAtReviewClaimAndAssignmentWithoutInterruptingActiveWork() throws Exception {
        doCallRealMethod().when(duty).onDuty(anyString(), anyString());
        String worker = "duty-worker-" + UUID.randomUUID();
        var ticket = create("duty-owner-" + UUID.randomUUID(), "Duty assignment", "requester");
        String dutyRoute = "/api/hpdi/v1/workspaces/lab-a/duty/me";
        // Mặc định đang trực nên tự tắt trước để kiểm tra giao cho người nghỉ bị từ chối
        mvc.perform(put(dutyRoute).with(as(worker,"lab-a","agent")).contentType("application/json").content("{\"onDuty\":false}"))
            .andExpect(status().isOk());
        review(ticket, "duty-reviewer", worker, null).andExpect(status().isConflict());
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
            ticketAlerts.check("lab-a", process, task, Map.of(worker, false));
            ticketAlerts.check("lab-a", process, task, Map.of(worker, false));
            assertEquals((round + 1) * 2, jdbc.queryForObject("select count(*) from hpdi_alert_event where process_instance_id=? and type not in ('REOPENED','REOPEN_UNCLAIMED')", Integer.class, process));
            mvc.perform(post(hpdi+task+"/start").with(as(worker,"lab-a","agent"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.reopenCount").value(round));
            engineState.elapse(task,60_000);
            mvc.perform(post(hpdi+task+"/complete").with(as(worker,"lab-a","agent")).contentType("application/json")
                .content("{\"expectedTaskKey\":\"handle\",\"outcome\":\"resolved\"}")).andExpect(status().isNoContent());
            ticketAlerts.check("lab-a", process, task, Map.of(worker, false));
            assertEquals((round + 1) * 2, jdbc.queryForObject("select count(*) from hpdi_alert_event where process_instance_id=? and type not in ('REOPENED','REOPEN_UNCLAIMED')", Integer.class, process));
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
        var result = body(mvc.perform(get(BASE.replace("lab-a", workspace) + "/statistics").param("q", query)
            .with(as(subject, workspace, roles))).andExpect(status().isOk()));
        long partition = 0;
        for (String field : List.of("intake", "following", "awaitingReview", "assigned", "inProgress", "pending", "reopened",
                "awaitingConfirmation", "closed", "unclassified")) partition += result.path(field).asLong();
        assertEquals(result.path("total").asLong(), partition);
        assertEquals(result.path("completed"), result.path("closed"));
        assertTrue(result.path("overdue").asLong() <= result.path("total").asLong());
        return result;
    }

    private void r12Count(String owner, String field) throws Exception {
        var result = statistics("lab-a", owner, null, "requester");
        assertEquals(1, result.path("total").asLong());
        assertEquals(1, result.path(field).asLong(), result.toString());
    }

    @Test void r12StatisticsUseOneRepeatableReadSnapshotDuringConcurrentWorkflowClosure() throws Exception {
        String owner = "r12-snapshot-" + UUID.randomUUID();
        var ticket = create(owner,"R12 snapshot","requester");
        String process = ticket.path("process").path("processInstanceId").asText();
        var once = new java.util.concurrent.atomic.AtomicBoolean();
        doAnswer(call -> {
            assertEquals("repeatable read",jdbc.queryForObject("show transaction_isolation",String.class));
            if (once.compareAndSet(false,true)) {
                var executor = Executors.newSingleThreadExecutor();
                try { executor.submit(() -> engineState.cancel(process)).get(15,TimeUnit.SECONDS); }
                finally { executor.shutdownNow(); }
            }
            return call.callRealMethod();
        }).when(statisticsObservations).snapshots(eq("lab-a"),anySet());
        r12Count(owner,"awaitingReview");
        r12Count(owner,"unclassified");
    }

    @Test void r12StatisticsBatchAcrossKeysetsWithoutPerTicketTimelineOrTaskReads() throws Exception {
        String owner = "r12-batch-" + UUID.randomUUID();
        var ticket = create(owner,"R12 batch","requester");
        jdbc.update("insert into process_scope(id,workspace,owner,process_key,business_key)"
            + " select ? || item::text,'lab-a',?,?,md5(? || item::text)::uuid::text from generate_series(1,1000) item",
            owner,owner,ServiceDeskProcess.REVIEWED_KEY,owner);
        jdbc.update("insert into servicedesk_ticket(id,workspace,creator,client_request_id,title,description,process_instance_id,process_key)"
            + " select md5(? || item::text)::uuid,'lab-a',?,md5('request-' || ? || item::text)::uuid,'R12 batch','Synthetic missing history',? || item::text,?"
            + " from generate_series(1,1000) item", owner,owner,owner,
            owner,ServiceDeskProcess.REVIEWED_KEY);
        clearInvocations(statisticsObservations,processViews,reviewViews);
        var result = statistics("lab-a",owner,null,"requester");
        assertEquals(1001,result.path("total").asLong());
        assertEquals(1000,result.path("unclassified").asLong());
        assertEquals(1,result.path("awaitingReview").asLong());
        verify(statisticsObservations,times(2)).snapshots(eq("lab-a"),anySet());
        verify(processViews,never()).timeline(anyString(),anyString());
        verify(reviewViews,never()).view(anyString(),any());
    }

    @Test void r12StatisticsTrackNativeExecutionReleaseRepeatedReworkAndRequesterClosure() throws Exception {
        String owner = "r12-owner-" + UUID.randomUUID();
        String worker = "r12-worker-" + UUID.randomUUID();
        var ticket = create(owner, "R12 lifecycle", "requester");
        r12Count(owner, "awaitingReview");
        String task = body(review(ticket, "coordinator", worker, null).andExpect(status().isOk())).path("taskId").asText();
        r12Count(owner, "assigned");
        String actions = "/api/hpdi/v1/workspaces/lab-a/tasks/";
        mvc.perform(post(actions + task + "/start").with(as(worker,"lab-a","agent"))).andExpect(status().isOk());
        r12Count(owner, "inProgress");
        mvc.perform(post(actions + task + "/pause").with(as(worker,"lab-a","agent")).contentType("application/json")
            .content("{\"reason\":\"WAITING_REQUESTER\"}")).andExpect(status().isOk());
        r12Count(owner, "pending");
        mvc.perform(post(actions + task + "/resume").with(as(worker,"lab-a","agent"))).andExpect(status().isOk());
        r12Count(owner, "inProgress");
        mvc.perform(post(actions + task + "/release").with(as(worker,"lab-a","agent")).contentType("application/json")
            .content("{\"reason\":\"HANDOVER\",\"note\":\"Continue diagnosis\"}")).andExpect(status().isNoContent());
        r12Count(owner, "awaitingReview");
        for (int cycle = 0; cycle < 3; cycle++) {
            task = body(review(ticket, "coordinator", worker, null).andExpect(status().isOk())).path("taskId").asText();
            r12Count(owner, cycle == 0 ? "assigned" : "reopened");
            mvc.perform(post(actions + task + "/start").with(as(worker,"lab-a","agent"))).andExpect(status().isOk());
            r12Count(owner, "inProgress");
            mvc.perform(post(actions + task + "/complete").with(as(worker,"lab-a","agent")).contentType("application/json")
                .content("{\"expectedTaskKey\":\"handle\",\"outcome\":\"resolved\"}")).andExpect(status().isNoContent());
            r12Count(owner, "awaitingConfirmation");
            var detail = body(mvc.perform(get(BASE + "/" + ticket.path("id").asText()).with(as(owner,"lab-a","requester"))).andExpect(status().isOk()));
            String outcome = cycle == 2 ? "accepted" : "rework";
            mvc.perform(post(actions + detail.path("taskId").asText() + "/complete").with(as(owner,"lab-a","requester")).contentType("application/json")
                .content(json.writeValueAsString(Map.of("expectedTaskKey","confirm","outcome",outcome)))).andExpect(status().isNoContent());
            r12Count(owner, cycle == 2 ? "closed" : "reopened");
        }
        long audits = jdbc.queryForObject("select count(*) from audit_event", Long.class);
        var rows = jdbc.queryForList("select * from servicedesk_confirmation where ticket_id=?::uuid",ticket.path("id").asText());
        r12Count(owner,"closed");
        assertEquals(audits,jdbc.queryForObject("select count(*) from audit_event",Long.class));
        assertEquals(rows,jdbc.queryForList("select * from servicedesk_confirmation where ticket_id=?::uuid",ticket.path("id").asText()));
    }

    @Test void r12StatisticsIncludeAutoClosureButExcludeResolvedHistoricalSlaBreach() throws Exception {
        String owner = "r12-auto-" + UUID.randomUUID();
        var ticket = awaitingConfirmation(owner);
        r12Count(owner,"awaitingConfirmation");
        jdbc.update("update servicedesk_sla set start_breached_history=true,resolve_breached_history=true where ticket_id=?::uuid",ticket.path("id").asText());
        assertEquals(0,statistics("lab-a",owner,null,"requester").path("overdue").asLong());
        authorizedTimerFixture(java.time.Instant.parse(ticket.path("confirmDeadlineAt").asText()));
        evaluate(ticket);
        r12Count(owner,"closed");
        assertEquals(0,statistics("lab-a",owner,null,"requester").path("overdue").asLong());
    }

    @Test void r12CurrentOverdueHonorsExactBoundaryAndR9PreservedOverdueDeadline() throws Exception {
        String owner = "r12-sla-" + UUID.randomUUID();
        var ticket = create(owner,"R12 SLA correction","requester");
        review(ticket,"coordinator","r12-sla-agent",null,"OTHER","HIGH").andExpect(status().isOk());
        var id = UUID.fromString(ticket.path("id").asText());
        var state = slaService.read("lab-a",id);
        doReturn(state.startBy().minusSeconds(1)).when(confirmationClock).instant();
        assertTrue(state.project(slaService.now()).warning());
        assertEquals(0,statistics("lab-a",owner,null,"requester").path("overdue").asLong());
        doReturn(state.startBy()).when(confirmationClock).instant();
        assertEquals(0,statistics("lab-a",owner,null,"requester").path("overdue").asLong());
        doReturn(state.startBy().plusSeconds(1)).when(confirmationClock).instant();
        assertEquals(1,statistics("lab-a",owner,null,"requester").path("overdue").asLong());
        mvc.perform(patch(BASE + "/" + id + "/classification").with(as("coordinator","lab-a","coordinator"))
            .contentType("application/json").content("{\"priority\":\"LOW\",\"reason\":\"Reduced verified impact\"}"))
            .andExpect(status().isOk());
        var amended = slaService.read("lab-a",id);
        assertTrue(amended.project(slaService.now()).startBreached());
        assertEquals(state.startBy(),amended.startBy());
        assertEquals(1,statistics("lab-a",owner,null,"requester").path("overdue").asLong());
        var before = jdbc.queryForList("select * from servicedesk_sla where ticket_id=?",id);
        var result = statistics("lab-a",owner,null,"requester");
        assertEquals(before,jdbc.queryForList("select * from servicedesk_sla where ticket_id=?",id));
        java.nio.file.Files.writeString(Path.of("target/r12-statistics-http-evidence.json"),json.writeValueAsString(List.of(result)));
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
