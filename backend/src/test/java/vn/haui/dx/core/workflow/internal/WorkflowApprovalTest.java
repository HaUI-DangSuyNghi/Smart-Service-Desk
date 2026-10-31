package vn.haui.dx.core.workflow.internal;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.flowable.engine.ProcessEngine;
import org.flowable.spring.SpringProcessEngineConfiguration;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import vn.haui.dx.core.audit.api.AuditService;
import vn.haui.dx.core.common.CoreException;
import vn.haui.dx.core.common.ErrorCode;
import vn.haui.dx.core.common.PageRequest;
import vn.haui.dx.core.identity.api.CurrentUser;
import vn.haui.dx.core.identity.api.Membership;
import vn.haui.dx.core.identity.api.WorkspaceRole;
import vn.haui.dx.core.workflow.api.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static vn.haui.dx.core.identity.api.WorkspaceRole.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@EnabledIfEnvironmentVariable(named = "WORKFLOW_TEST_JDBC_URL", matches = ".+/workflow_approval")
class WorkflowApprovalTest {
    private ProcessEngine engine;
    private JdbcTemplate jdbc;
    private DataSourceTransactionManager manager;
    private ProcessRegistry registry;
    private final ApprovalProcesses registration = new ApprovalProcesses();
    private final User requester = new User("requester", Set.of(new Membership("lab-a", REQUESTER)));
    private final User reviewer = new User("reviewer", Set.of(new Membership("lab-a", COORDINATOR)));
    private final User agent = new User("agent", Set.of(new Membership("lab-a", AGENT)));

    private record User(String sub, Set<Membership> memberships) implements CurrentUser {
        public String username() { return sub; }
        public boolean platformAdmin() { return false; }
        public boolean isMember(String workspace) { return memberships.stream().anyMatch(member -> member.workspace().equals(workspace)); }
        public boolean hasRole(String workspace, WorkspaceRole... roles) {
            return memberships.stream().anyMatch(member -> member.workspace().equals(workspace) && List.of(roles).contains(member.role()));
        }
    }

    @BeforeAll void startEngine() {
        var source = new DriverManagerDataSource(System.getenv("WORKFLOW_TEST_JDBC_URL"), "workflow", System.getenv("WORKFLOW_TEST_DB_PASSWORD"));
        jdbc = new JdbcTemplate(source);
        manager = new DataSourceTransactionManager(source);
        jdbc.execute("create table process_scope(id varchar(100) primary key, workspace varchar(100) not null, owner varchar(100) not null, "
            + "process_key varchar(100) not null, business_key varchar(200) not null, unique(workspace,process_key,business_key))");
        jdbc.execute("create table test_audit(action varchar(100), resource varchar(200), actor varchar(100))");
        var config = new SpringProcessEngineConfiguration();
        config.setDataSource(source);
        config.setTransactionManager(manager);
        config.setDatabaseSchemaUpdate("true");
        config.setHistory("full");
        config.setAsyncExecutorActivate(false);
        engine = config.buildProcessEngine();
        var deployment = engine.getRepositoryService().createDeployment();
        for (var policy : registration.policies())
            deployment.addClasspathResource("apps/samples/process/" + policy.processKey() + ".bpmn20.xml");
        deployment.deploy();
        registry = new ProcessRegistry(List.of(registration), engine.getRepositoryService());
    }

    @AfterAll void closeEngine() { if (engine != null) engine.close(); }

    private AuditService audit(User user) {
        var audit = mock(AuditService.class);
        doAnswer(call -> {
            jdbc.update("insert into test_audit values (?,?,?)", call.getArgument(0), call.getArgument(2), user.sub());
            return null;
        }).when(audit).record(anyString(), anyString(), anyString(), anyString(), anyMap());
        return audit;
    }

    private FlowableWorkflowService service(User user) { return service(user, audit(user)); }
    private FlowableWorkflowService service(User user, AuditService audit) {
        return new FlowableWorkflowService(engine, jdbc, user, audit, registry, manager,
            vn.haui.dx.core.identity.PermissionTestDirectory.allowed());
    }
    @Test void revokedTaskPermissionBlocksAlreadyAssignedCompletionWithoutChangingEngineOrAudit() {
        var process = start("document-review");
        var review = task(process);
        service(reviewer).claim("lab-a", review.id());
        var directory = vn.haui.dx.core.identity.PermissionTestDirectory.allowed();
        var assigned = new FlowableWorkflowService(engine, jdbc, reviewer, audit(reviewer), registry, manager, directory);
        when(directory.rolePermissions("lab-a")).thenReturn(Map.of());
        long audits = jdbc.queryForObject("select count(*) from test_audit", Long.class);
        denied(ErrorCode.FORBIDDEN, () -> assigned.complete("lab-a", review.id(), review.key(), "approved", null));
        assertNotNull(engine.getTaskService().createTaskQuery().taskId(review.id()).singleResult());
        assertEquals(audits, jdbc.queryForObject("select count(*) from test_audit", Long.class));
        when(directory.rolePermissions("lab-a")).thenThrow(CoreException.unavailable("down"));
        denied(ErrorCode.SERVICE_UNAVAILABLE, () -> assigned.complete("lab-a", review.id(), review.key(), "approved", null));
        assertNotNull(engine.getTaskService().createTaskQuery().taskId(review.id()).singleResult());
        service(reviewer).complete("lab-a", review.id(), review.key(), "approved", null);
    }

    private ProcessRef start(String key) { return service(requester).start("lab-a", key, UUID.randomUUID().toString(), Map.of("title", "Example")); }
    private TaskView task(ProcessRef process) { return service(requester).tasksOf("lab-a", process.processInstanceId()).get(0); }
    private FlowableWorkflowService executionService(User user) {
        return executionService(user, audit(user));
    }
    private FlowableWorkflowService executionService(User user, AuditService audit) {
        return executionService(user, audit, vn.haui.dx.core.identity.PermissionTestDirectory.allowed());
    }
    private FlowableWorkflowService executionService(User user, AuditService audit,
            vn.haui.dx.core.identity.api.WorkspaceDirectory directory) {
        return new FlowableWorkflowService(engine, jdbc, user, audit, executionRegistry(), manager, directory);
    }
    private ProcessRegistry executionRegistry() {
        ProcessRegistration work = new ProcessRegistration() {
            public List<ProcessPolicy> policies() { return registration.policies(); }
            public Map<String, Set<String>> executionTasks() { return Map.of("equipment-approval", Set.of("handover")); }
        };
        return new ProcessRegistry(List.of(work), engine.getRepositoryService());
    }
    private TaskView executionTask() {
        return executionTask(agent);
    }
    private TaskView executionTask(User worker) {
        var process = start("equipment-approval");
        var approval = task(process);
        service(reviewer).claim("lab-a", approval.id());
        service(reviewer).complete("lab-a", approval.id(), "approve", "approved", null);
        var handover = task(process);
        service(worker).claim("lab-a", handover.id());
        return handover;
    }

    @Test void queueProjectionDoesNotHydrateAReassignedTaskUnderThePreviousAssignee() {
        var worker = new User("snapshot-" + UUID.randomUUID(), agent.memberships());
        var task = executionTask(worker);
        var reassigned = new java.util.concurrent.atomic.AtomicBoolean();
        var racingJdbc = new JdbcTemplate(jdbc.getDataSource()) {
            private void reassign(String sql) {
                if (sql.startsWith("select t.id_") && reassigned.compareAndSet(false, true))
                    engine.getTaskService().setAssignee(task.id(), "different-assignee");
            }
            @Override public <T> List<T> queryForList(String sql, Class<T> type, Object... args) {
                var result = super.queryForList(sql, type, args);
                reassign(sql);
                return result;
            }
            @Override public <T> List<T> query(String sql, org.springframework.jdbc.core.RowMapper<T> mapper, Object... args) {
                var result = super.query(sql, mapper, args);
                reassign(sql);
                return result;
            }
        };
        try {
            var work = new FlowableWorkflowService(engine, racingJdbc, worker, audit(worker), executionRegistry(), manager,
                vn.haui.dx.core.identity.PermissionTestDirectory.allowed());
            var result = work.queue("lab-a", null, PageRequest.of(0, 20));
            assertTrue(reassigned.get());
            assertEquals(1, result.total());
            assertEquals(worker.sub(), result.items().get(0).task().assignee());
            assertEquals("different-assignee", engine.getTaskService().createTaskQuery().taskId(task.id()).singleResult().getAssignee());
        } finally {
            engine.getRuntimeService().deleteProcessInstance(task.processInstanceId(), "queue snapshot test cleanup");
        }
    }

    @Test void nonOptedInProcessesAndApprovalStepsDoNotExecuteOrEnterTheWorkQueue() {
        var worker = new User("opt-in-" + UUID.randomUUID(), agent.memberships());
        var task = executionTask(worker);
        var awaitingApproval = start("equipment-approval");
        try {
            denied(ErrorCode.CONFLICT, () -> service(worker).execute("lab-a", task.id(), TaskExecution.Action.START, null, null));
            assertTrue(service(worker).queue("lab-a", null, PageRequest.of(0, 20)).items().isEmpty());
            var approval = task(awaitingApproval);
            service(reviewer).claim("lab-a", approval.id());
            denied(ErrorCode.CONFLICT, () -> executionService(reviewer).execute("lab-a", approval.id(), TaskExecution.Action.START, null, null));
            assertTrue(executionService(reviewer).queue("lab-a", null, PageRequest.of(0, 100)).items().isEmpty());
            assertNull(engine.getTaskService().getVariableLocal(task.id(), "executionState"));
            service(worker).complete("lab-a", task.id(), "handover", "delivered", null);
        } finally {
            if (engine.getRuntimeService().createProcessInstanceQuery().processInstanceId(task.processInstanceId()).count() > 0)
                engine.getRuntimeService().deleteProcessInstance(task.processInstanceId(), "legacy execution test cleanup");
            engine.getRuntimeService().deleteProcessInstance(awaitingApproval.processInstanceId(), "approval exclusion test cleanup");
        }
    }

    @Test void queueOrdersActiveThenPriorityThenDeadlineThenCreationAndIdWithStablePagination() {
        var worker = new User("queue-" + UUID.randomUUID(), agent.memberships());
        var work = executionService(worker);
        var tasks = java.util.stream.IntStream.range(0, 5).mapToObj(index -> executionTask(worker)).toList();
        try {
            var service = engine.getTaskService();
            for (var task : tasks) {
                service.setPriority(task.id(), 50);
                service.setDueDate(task.id(), java.util.Date.from(java.time.Instant.parse("2026-11-01T00:00:00Z")));
                jdbc.update("update act_ru_task set create_time_=? where id_=?",
                    java.sql.Timestamp.from(java.time.Instant.parse("2026-10-01T00:00:00Z")), task.id());
            }
            var active = tasks.get(0);
            service.setPriority(active.id(), 1);
            work.execute("lab-a", active.id(), TaskExecution.Action.START, null, null);
            service.setPriority(tasks.get(1).id(), 100);
            service.setDueDate(tasks.get(2).id(), java.util.Date.from(java.time.Instant.parse("2026-10-31T00:00:00Z")));
            var tie = List.of(tasks.get(3).id(), tasks.get(4).id()).stream().sorted().toList();
            var expected = new java.util.ArrayList<>(List.of(active.id(), tasks.get(1).id(), tasks.get(2).id()));
            expected.addAll(tie);
            var result = work.queue("lab-a", null, PageRequest.of(0, 100));
            assertEquals(expected, result.items().stream().map(item -> item.task().id()).toList());
            assertEquals(5, result.total());
            assertEquals(expected.subList(2, 4), work.queue("lab-a", null, PageRequest.of(1, 2)).items().stream()
                .map(item -> item.task().id()).toList());
            assertTrue(work.queue("lab-a", null, PageRequest.of(3, 2)).items().isEmpty());
            assertEquals(expected, executionService(reviewer).queue("lab-a", worker.sub(), PageRequest.of(0, 100))
                .items().stream().map(item -> item.task().id()).toList());
        } finally {
            tasks.forEach(task -> engine.getRuntimeService().deleteProcessInstance(task.processInstanceId(), "queue test cleanup"));
        }
    }

    @Test void executionRechecksCurrentPermissionCandidateRoleOwnerAndResourceWorkspace() {
        var task = executionTask();
        try {
            var directory = vn.haui.dx.core.identity.PermissionTestDirectory.allowed();
            when(directory.rolePermissions("lab-a")).thenReturn(Map.of());
            denied(ErrorCode.FORBIDDEN, () -> executionService(agent, audit(agent), directory)
                .execute("lab-a", task.id(), TaskExecution.Action.START, null, null));
            when(directory.rolePermissions("lab-a")).thenThrow(CoreException.unavailable("synthetic directory outage"));
            denied(ErrorCode.SERVICE_UNAVAILABLE, () -> executionService(agent, audit(agent), directory)
                .execute("lab-a", task.id(), TaskExecution.Action.START, null, null));
            var foreign = new User(agent.sub(), Set.of(new Membership("lab-b", AGENT)));
            denied(ErrorCode.NOT_FOUND, () -> executionService(foreign).execute("lab-b", task.id(), TaskExecution.Action.START, null, null));
            engine.getTaskService().setAssignee(task.id(), reviewer.sub());
            denied(ErrorCode.FORBIDDEN, () -> executionService(reviewer).execute("lab-a", task.id(), TaskExecution.Action.START, null, null));
            engine.getTaskService().setAssignee(task.id(), agent.sub());
            jdbc.update("update process_scope set owner=? where id=?", agent.sub(), task.processInstanceId());
            denied(ErrorCode.FORBIDDEN, () -> executionService(agent).execute("lab-a", task.id(), TaskExecution.Action.START, null, null));
            assertNull(engine.getTaskService().getVariableLocal(task.id(), "executionState"));
        } finally {
            jdbc.update("update process_scope set owner=? where id=?", requester.sub(), task.processInstanceId());
            engine.getRuntimeService().deleteProcessInstance(task.processInstanceId(), "authorization test cleanup");
        }
    }

    @Test void executionIntervalsExcludePendingTimeAndCompletionClosesLastInterval() {
        var task = executionTask();
        var work = executionService(agent);
        try {
            denied(ErrorCode.CONFLICT, () -> work.complete("lab-a", task.id(), "handover", "delivered", null));
            assertEquals(TaskExecution.State.IN_PROGRESS, work.execute("lab-a", task.id(), TaskExecution.Action.START, null, null).state());
            denied(ErrorCode.CONFLICT, () -> work.execute("lab-a", task.id(), TaskExecution.Action.START, null, null));
            denied(ErrorCode.VALIDATION_FAILED, () -> work.execute("lab-a", task.id(), TaskExecution.Action.PAUSE, null, null));
            long started = System.currentTimeMillis() - 60_000;
            engine.getTaskService().setVariableLocal(task.id(), "executionStarted", started);
            var paused = work.execute("lab-a", task.id(), TaskExecution.Action.PAUSE, "WAITING_PARTS", "Await replacement");
            assertEquals(TaskExecution.State.PENDING, paused.state());
            assertTrue(paused.workedMillis() >= 60_000);
            assertNull(paused.startedAt());
            var waiting = work.queue("lab-a", null, PageRequest.of(0, 100)).items().stream()
                .filter(item -> item.task().id().equals(task.id())).findFirst().orElseThrow();
            assertEquals(paused.workedMillis(), waiting.workedMillis());
            assertEquals("WAITING_PARTS", waiting.pendingReason());
            denied(ErrorCode.CONFLICT, () -> work.execute("lab-a", task.id(), TaskExecution.Action.PAUSE, "WAITING_PARTS", null));
            var resumed = work.execute("lab-a", task.id(), TaskExecution.Action.RESUME, null, null);
            assertNull(resumed.pendingReason());
            denied(ErrorCode.CONFLICT, () -> work.execute("lab-a", task.id(), TaskExecution.Action.RESUME, null, null));
            work.complete("lab-a", task.id(), "handover", "delivered", null);
            var historic = engine.getHistoryService().createHistoricTaskInstanceQuery().taskId(task.id())
                .includeTaskLocalVariables().singleResult();
            assertNull(historic.getTaskLocalVariables().get("executionStarted"));
            assertTrue((Long) historic.getTaskLocalVariables().get("executionMillis") >= paused.workedMillis());
        } finally {
            if (engine.getTaskService().createTaskQuery().taskId(task.id()).count() > 0)
                engine.getRuntimeService().deleteProcessInstance(task.processInstanceId(), "execution test cleanup");
        }
    }

    @Test void concurrentExecutionStartsAndResumesHaveExactlyOneWinner() throws Exception {
        var first = executionTask();
        var second = executionTask();
        try {
            race(() -> executionService(agent).execute("lab-a", first.id(), TaskExecution.Action.START, null, null),
                () -> executionService(agent).execute("lab-a", second.id(), TaskExecution.Action.START, null, null));
            var active = engine.getTaskService().createTaskQuery().taskAssignee(agent.sub())
                .taskVariableValueEquals("executionState", "IN_PROGRESS").singleResult();
            assertNotNull(active);
            var queued = active.getId().equals(first.id()) ? second : first;
            executionService(agent).execute("lab-a", active.getId(), TaskExecution.Action.PAUSE, "WAITING_REQUESTER", null);
            executionService(agent).execute("lab-a", queued.id(), TaskExecution.Action.START, null, null);
            executionService(agent).execute("lab-a", queued.id(), TaskExecution.Action.PAUSE, "WAITING_PARTS", null);
            race(() -> executionService(agent).execute("lab-a", first.id(), TaskExecution.Action.RESUME, null, null),
                () -> executionService(agent).execute("lab-a", second.id(), TaskExecution.Action.RESUME, null, null));
        } finally {
            engine.getRuntimeService().deleteProcessInstance(first.processInstanceId(), "execution test cleanup");
            engine.getRuntimeService().deleteProcessInstance(second.processInstanceId(), "execution test cleanup");
        }
    }

    @Test void executionAuthorizationAndAuditFailureLeaveStateUnchanged() {
        var task = executionTask();
        try {
            denied(ErrorCode.FORBIDDEN, () -> executionService(reviewer).execute("lab-a", task.id(), TaskExecution.Action.START, null, null));
            denied(ErrorCode.FORBIDDEN, () -> executionService(agent).execute("lab-b", task.id(), TaskExecution.Action.START, null, null));
            var failingAudit = mock(AuditService.class);
            doThrow(new IllegalStateException("synthetic audit outage")).when(failingAudit)
                .record(anyString(), anyString(), anyString(), anyString(), anyMap());
            assertThrows(IllegalStateException.class, () -> executionService(agent, failingAudit)
                .execute("lab-a", task.id(), TaskExecution.Action.START, null, null));
            assertNull(engine.getTaskService().getVariableLocal(task.id(), "executionState"));
            assertNull(engine.getTaskService().getVariableLocal(task.id(), "executionStarted"));
            denied(ErrorCode.FORBIDDEN, () -> executionService(requester).queue("lab-a", agent.sub(), PageRequest.of(0, 20)));
            assertTrue(executionService(agent).queue("lab-a", null, PageRequest.of(0, 100)).items().stream()
                .anyMatch(item -> item.task().id().equals(task.id())));
        } finally {
            engine.getRuntimeService().deleteProcessInstance(task.processInstanceId(), "execution test cleanup");
        }
    }

    @Test void activeWorkCannotBeReassignedAndPausedWorkKeepsElapsedTimeForTheNewAssignee() {
        var first = new User("first-" + UUID.randomUUID(), agent.memberships());
        var second = new User("second-" + UUID.randomUUID(), agent.memberships());
        var task = executionTask(first);
        try {
            var directory = vn.haui.dx.core.identity.PermissionTestDirectory.allowed();
            when(directory.members("lab-a")).thenReturn(List.of(new vn.haui.dx.core.identity.api.DirectoryMember(
                second.sub(), second.sub(), null, Set.of(AGENT))));
            var coordinator = executionService(reviewer, audit(reviewer), directory);
            executionService(first).execute("lab-a", task.id(), TaskExecution.Action.START, null, null);
            denied(ErrorCode.CONFLICT, () -> coordinator.assign("lab-a", task.id(), second.sub()));
            assertEquals(first.sub(), engine.getTaskService().createTaskQuery().taskId(task.id()).singleResult().getAssignee());
            var paused = executionService(first).execute("lab-a", task.id(), TaskExecution.Action.PAUSE, "WAITING_EXTERNAL", null);
            coordinator.assign("lab-a", task.id(), second.sub());
            var queued = executionService(second).queue("lab-a", null, PageRequest.of(0, 20)).items().get(0);
            assertEquals(TaskExecution.State.NOT_STARTED, queued.state());
            assertEquals(paused.workedMillis(), queued.workedMillis());
            assertNull(queued.pendingReason());
            denied(ErrorCode.FORBIDDEN, () -> executionService(first).execute("lab-a", task.id(), TaskExecution.Action.START, null, null));
            assertEquals(TaskExecution.State.IN_PROGRESS, executionService(second)
                .execute("lab-a", task.id(), TaskExecution.Action.START, null, null).state());
        } finally {
            engine.getRuntimeService().deleteProcessInstance(task.processInstanceId(), "reassignment test cleanup");
        }
    }

    @Test void duplicateConcurrentStartOnTheSameTaskOpensOnlyOneInterval() throws Exception {
        var task = executionTask();
        try {
            Callable<?> start = () -> executionService(agent).execute("lab-a", task.id(), TaskExecution.Action.START, null, null);
            race(start, start);
            assertEquals("IN_PROGRESS", engine.getTaskService().getVariableLocal(task.id(), "executionState"));
            assertNotNull(engine.getTaskService().getVariableLocal(task.id(), "executionStarted"));
        } finally {
            engine.getRuntimeService().deleteProcessInstance(task.processInstanceId(), "duplicate start test cleanup");
        }
    }

    @Test void pauseAndCompletionAuditFailuresRollBackIntervalsAndConcurrentPauseResumeDoNotDoubleCount() throws Exception {
        var worker = new User("interval-" + UUID.randomUUID(), agent.memberships());
        var task = executionTask(worker);
        var work = executionService(worker);
        try {
            work.execute("lab-a", task.id(), TaskExecution.Action.START, null, null);
            long started = System.currentTimeMillis() - 60_000;
            engine.getTaskService().setVariableLocal(task.id(), "executionStarted", started);
            var failingAudit = audit(worker);
            doThrow(new IllegalStateException("synthetic pause audit failure")).when(failingAudit)
                .record(eq("task.pause"), anyString(), anyString(), anyString(), anyMap());
            assertThrows(IllegalStateException.class, () -> executionService(worker, failingAudit)
                .execute("lab-a", task.id(), TaskExecution.Action.PAUSE, "WAITING_EXTERNAL", null));
            assertEquals("IN_PROGRESS", engine.getTaskService().getVariableLocal(task.id(), "executionState"));
            assertEquals(started, engine.getTaskService().getVariableLocal(task.id(), "executionStarted"));
            assertNull(engine.getTaskService().getVariableLocal(task.id(), "executionMillis"));
            assertEquals(0, jdbc.queryForObject("select count(*) from test_audit where action='task.work-interval' and resource=?",
                Integer.class, task.id()));
            Callable<?> pause = () -> work.execute("lab-a", task.id(), TaskExecution.Action.PAUSE, "WAITING_EXTERNAL", null);
            race(pause, pause);
            long accumulated = (Long) engine.getTaskService().getVariableLocal(task.id(), "executionMillis");
            assertTrue(accumulated >= 60_000);
            Callable<?> resume = () -> work.execute("lab-a", task.id(), TaskExecution.Action.RESUME, null, null);
            race(resume, resume);
            assertEquals(accumulated, engine.getTaskService().getVariableLocal(task.id(), "executionMillis"));
            var resumedAt = engine.getTaskService().getVariableLocal(task.id(), "executionStarted");
            var failingCompletion = audit(worker);
            doThrow(new IllegalStateException("synthetic completion audit failure")).when(failingCompletion)
                .record(eq("task.complete"), anyString(), anyString(), anyString(), anyMap());
            assertThrows(IllegalStateException.class, () -> executionService(worker, failingCompletion)
                .complete("lab-a", task.id(), "handover", "delivered", null));
            assertNotNull(engine.getTaskService().createTaskQuery().taskId(task.id()).singleResult());
            assertEquals("IN_PROGRESS", engine.getTaskService().getVariableLocal(task.id(), "executionState"));
            assertEquals(resumedAt, engine.getTaskService().getVariableLocal(task.id(), "executionStarted"));
            assertEquals(accumulated, engine.getTaskService().getVariableLocal(task.id(), "executionMillis"));
            assertEquals(1, jdbc.queryForObject("select count(*) from test_audit where action='task.work-interval' and resource=?",
                Integer.class, task.id()));
        } finally {
            engine.getRuntimeService().deleteProcessInstance(task.processInstanceId(), "interval atomicity test cleanup");
        }
    }
    private void denied(ErrorCode code, org.junit.jupiter.api.function.Executable action) {
        assertEquals(code, assertThrows(CoreException.class, action).code());
    }

    @Test void documentApprovalAndRejectionHaveRealHistory() {
        for (String outcome : List.of("approved", "rejected")) {
            var process = start("document-review");
            var task = task(process);
            assertNull(task.assignee());
            assertEquals(List.of("lab-a:coordinator"), task.candidateGroups());
            assertEquals("reviewer", service(reviewer).claim("lab-a", task.id()).assignee());
            service(reviewer).complete("lab-a", task.id(), "review", outcome, "Reviewed");
            var history = service(requester).history("lab-a", process.processInstanceId());
            assertNotNull(history.endedAt());
            assertTrue(history.currentTasks().isEmpty());
            assertEquals(1, history.steps().size());
            assertEquals(Map.of("title", "Example"), history.variables());
            var step = history.steps().get(0);
            assertEquals(outcome, step.outcome());
            assertEquals("Reviewed", step.comment());
            assertEquals("reviewer", step.assignee());
            assertNotNull(step.claimedAt());
            assertNotNull(step.endedAt());
            assertEquals(1, engine.getHistoryService().createHistoricActivityInstanceQuery()
                .processInstanceId(process.processInstanceId()).activityId(outcome).count());
        }
    }

    @Test void equipmentRejectionSkipsHandoverAndApprovalRequiresAgent() {
        var rejected = start("equipment-approval");
        var rejectTask = task(rejected);
        service(reviewer).claim("lab-a", rejectTask.id());
        service(reviewer).complete("lab-a", rejectTask.id(), "approve", "rejected", null);
        assertNotNull(service(requester).history("lab-a", rejected.processInstanceId()).endedAt());
        assertEquals(1, service(requester).history("lab-a", rejected.processInstanceId()).steps().size());
        var approved = start("equipment-approval");
        var approval = task(approved);
        service(reviewer).claim("lab-a", approval.id());
        service(reviewer).complete("lab-a", approval.id(), "approve", "approved", null);
        var handover = task(approved);
        assertEquals("handover", handover.key());
        denied(ErrorCode.FORBIDDEN, () -> service(reviewer).claim("lab-a", handover.id()));
        service(agent).claim("lab-a", handover.id());
        service(agent).complete("lab-a", handover.id(), "handover", "delivered", null);
        assertNotNull(service(agent).history("lab-a", approved.processInstanceId()).endedAt());
        assertEquals(2, service(agent).history("lab-a", approved.processInstanceId()).steps().size());
    }

    @Test void authorizationValidationAndReplay() {
        var process = start("document-review");
        var task = task(process);
        denied(ErrorCode.FORBIDDEN, () -> service(requester).claim("lab-a", task.id()));
        denied(ErrorCode.FORBIDDEN, () -> service(agent).claim("lab-a", task.id()));
        denied(ErrorCode.NOT_FOUND, () -> service(agent).history("lab-a", process.processInstanceId()));
        denied(ErrorCode.FORBIDDEN, () -> service(reviewer).claim("lab-b", task.id()));
        var foreign = new User("foreign", Set.of(new Membership("lab-b", COORDINATOR)));
        denied(ErrorCode.NOT_FOUND, () -> service(foreign).claim("lab-b", task.id()));
        denied(ErrorCode.NOT_FOUND, () -> service(foreign).history("lab-b", process.processInstanceId()));
        service(reviewer).claim("lab-a", task.id());
        denied(ErrorCode.CONFLICT, () -> service(reviewer).claim("lab-a", task.id()));
        denied(ErrorCode.CONFLICT, () -> service(reviewer).complete("lab-a", task.id(), "wrong", "approved", null));
        denied(ErrorCode.FORBIDDEN, () -> service(agent).complete("lab-a", task.id(), "review", "approved", null));
        denied(ErrorCode.VALIDATION_FAILED, () -> service(reviewer).complete("lab-a", task.id(), "review", "bad", null));
        service(reviewer).complete("lab-a", task.id(), "review", "approved", null);
        denied(ErrorCode.CONFLICT, () -> service(reviewer).complete("lab-a", task.id(), "review", "approved", null));
        denied(ErrorCode.CONFLICT, () -> service(reviewer).claim("lab-a", task.id()));
        denied(ErrorCode.VALIDATION_FAILED, () -> service(requester).start("lab-a", "document-review", "x", Map.of("initiator", "spoof")));
        denied(ErrorCode.NOT_FOUND, () -> service(requester).start("lab-a", "unknown", "x", Map.of()));
    }

    @Test void creatorCannotReviewEvenWithCoordinatorRoleAndInboxIsScoped() {
        var process = service(reviewer).start("lab-a", "document-review", UUID.randomUUID().toString(), Map.of());
        var ownTask = service(reviewer).tasksOf("lab-a", process.processInstanceId()).get(0);
        denied(ErrorCode.FORBIDDEN, () -> service(reviewer).claim("lab-a", ownTask.id()));
        var other = start("document-review");
        var candidate = service(reviewer).inbox("lab-a", new InboxFilter(InboxFilter.Scope.CANDIDATE), new PageRequest(0, 100));
        assertFalse(candidate.items().stream().anyMatch(item -> item.id().equals(ownTask.id())));
        assertTrue(candidate.items().stream().anyMatch(item -> item.id().equals(task(other).id())));
        assertTrue(service(agent).inbox("lab-a", new InboxFilter(InboxFilter.Scope.CANDIDATE), new PageRequest(0, 100)).items().isEmpty());
        var otherTask = task(other);
        service(reviewer).claim("lab-a", otherTask.id());
        assertTrue(service(reviewer).inbox("lab-a", new InboxFilter(InboxFilter.Scope.ASSIGNED), new PageRequest(0, 100))
            .items().stream().anyMatch(item -> item.id().equals(otherTask.id())));
        denied(ErrorCode.VALIDATION_FAILED, () -> service(reviewer).inbox("lab-a", new InboxFilter(InboxFilter.Scope.CANDIDATE), new PageRequest(Integer.MAX_VALUE, 100)));
    }

    @Test void coordinatorAssignsOnlyToCandidateMembersAndSeesAllTasksAndStatistics() {
        var directory = mock(vn.haui.dx.core.identity.api.WorkspaceDirectory.class);
        when(directory.members("lab-a")).thenReturn(List.of(
            new vn.haui.dx.core.identity.api.DirectoryMember("reviewer", "reviewer", null, Set.of(COORDINATOR)),
            new vn.haui.dx.core.identity.api.DirectoryMember("agent", "agent", null, Set.of(AGENT)),
            new vn.haui.dx.core.identity.api.DirectoryMember("requester", "requester", null, Set.of(REQUESTER))));
        var coordinator = new FlowableWorkflowService(engine, jdbc, reviewer, audit(reviewer), registry, manager, directory);
        var process = start("equipment-approval");
        var approve = task(process);

        // Bước duyệt thuộc nhóm coordinator: không giao cho agent, không giao cho người tạo
        denied(ErrorCode.VALIDATION_FAILED, () -> coordinator.assign("lab-a", approve.id(), "agent"));
        denied(ErrorCode.FORBIDDEN, () -> coordinator.assign("lab-a", approve.id(), "requester"));
        assertEquals("reviewer", coordinator.assign("lab-a", approve.id(), "reviewer").assignee());
        service(reviewer).complete("lab-a", approve.id(), "approve", "approved", null);

        // Bước bàn giao thuộc nhóm agent: coordinator giao được cho agent; người không phải coordinator thì không
        var handover = task(process);
        assertEquals("agent", coordinator.assign("lab-a", handover.id(), "agent").assignee());
        var asAgent = new FlowableWorkflowService(engine, jdbc, agent, audit(agent), registry, manager, directory);
        denied(ErrorCode.FORBIDDEN, () -> asAgent.assign("lab-a", handover.id(), "agent"));

        // Tab "Tất cả việc" chỉ coordinator
        assertTrue(coordinator.inbox("lab-a", new InboxFilter(InboxFilter.Scope.ALL), new PageRequest(0, 100))
            .items().stream().anyMatch(item -> item.id().equals(handover.id())));
        denied(ErrorCode.FORBIDDEN, () -> asAgent.inbox("lab-a", new InboxFilter(InboxFilter.Scope.ALL), new PageRequest(0, 100)));

        // Coordinator là reader của quy trình mẫu nên thấy yêu cầu do người khác tạo
        assertTrue(coordinator.readable("lab-a", new PageRequest(0, 100)).items().stream()
            .anyMatch(item -> item.id().equals(process.processInstanceId()) && "requester".equals(item.initiator())));

        var now = java.time.Instant.now();
        var stats = coordinator.statistics("lab-a", now.minus(java.time.Duration.ofDays(1)), now.plusSeconds(60),
            java.time.ZoneId.of("Asia/Ho_Chi_Minh"));
        assertTrue(stats.processes().stream().anyMatch(count -> count.processKey().equals("equipment-approval") && count.started() > 0));
        assertFalse(stats.daily().isEmpty());
        denied(ErrorCode.FORBIDDEN, () -> asAgent.statistics("lab-a", now.minusSeconds(60), now, java.time.ZoneId.of("UTC")));
    }

    @Test void startedByMeListsOnlyOwnProcessesNewestFirstWithActiveSteps() {
        var older = start("equipment-approval");
        var newer = start("document-review");
        service(reviewer).start("lab-a", "document-review", UUID.randomUUID().toString(), Map.of());
        var mine = service(requester).startedByMe("lab-a", new PageRequest(0, 100)).items();
        var ids = mine.stream().map(ProcessSummary::id).toList();
        assertTrue(ids.indexOf(newer.processInstanceId()) < ids.indexOf(older.processInstanceId()));
        assertTrue(mine.stream().allMatch(item -> service(requester).history("lab-a", item.id()).initiator().equals("requester")));
        var summary = mine.stream().filter(item -> item.id().equals(older.processInstanceId())).findFirst().orElseThrow();
        assertEquals(List.of("approve"), summary.activeTaskKeys());
        assertNull(summary.endedAt());
        denied(ErrorCode.FORBIDDEN, () -> service(requester).startedByMe("lab-b", new PageRequest(0, 100)));
    }

    @Test void duplicateStartAndAuditFailureRollBackEngineAndScope() {
        var process = start("document-review");
        long before = engine.getRuntimeService().createProcessInstanceQuery().count();
        denied(ErrorCode.CONFLICT, () -> service(requester).start("lab-a", "document-review", process.businessKey(), Map.of()));
        assertEquals(before, engine.getRuntimeService().createProcessInstanceQuery().count());
        var failedAudit = audit(requester);
        doAnswer(call -> {
            jdbc.update("insert into test_audit values ('failure', 'failure', 'failure')");
            throw new IllegalStateException("Audit unavailable");
        }).when(failedAudit).record(anyString(), anyString(), anyString(), anyString(), anyMap());
        String key = UUID.randomUUID().toString();
        assertThrows(IllegalStateException.class, () -> service(requester, failedAudit).start("lab-a", "document-review", key, Map.of()));
        assertEquals(before, engine.getRuntimeService().createProcessInstanceQuery().count());
        assertEquals(0, jdbc.queryForObject("select count(*) from process_scope where business_key=?", Integer.class, key));
        assertEquals(0, engine.getHistoryService().createHistoricProcessInstanceQuery().processInstanceBusinessKey(key).count());
        assertEquals(0, jdbc.queryForObject("select count(*) from test_audit where action='failure'", Integer.class));
        var task = task(process);
        assertThrows(IllegalStateException.class, () -> service(reviewer, failedAudit).claim("lab-a", task.id()));
        assertNull(task(process).assignee());
        service(reviewer).claim("lab-a", task.id());
        assertThrows(IllegalStateException.class, () -> service(reviewer, failedAudit).complete("lab-a", task.id(), "review", "approved", null));
        assertEquals(task.id(), task(process).id());
        assertNull(service(requester).history("lab-a", process.processInstanceId()).steps().get(0).outcome());
        assertEquals(0, jdbc.queryForObject("select count(*) from test_audit where action='failure'", Integer.class));
    }

    @Test void concurrentClaimsAndCompletionsHaveOneWinner() throws Exception {
        var process = start("document-review");
        var task = task(process);
        var second = new User("second", reviewer.memberships());
        race(() -> service(reviewer).claim("lab-a", task.id()), () -> service(second).claim("lab-a", task.id()));
        var winner = task(process).assignee().equals(reviewer.sub()) ? reviewer : second;
        Callable<Object> complete = () -> { service(winner).complete("lab-a", task.id(), "review", "approved", null); return null; };
        race(complete, complete);
        assertEquals(1, jdbc.queryForObject("select count(*) from test_audit where action='task.claim' and resource=?", Integer.class, task.id()));
        assertEquals(1, jdbc.queryForObject("select count(*) from test_audit where action='task.complete' and resource=?", Integer.class, task.id()));
    }

    @Test void concurrentStartsHaveOneEngineInstanceAndOneAudit() throws Exception {
        String key = UUID.randomUUID().toString();
        Callable<Object> start = () -> service(requester).start("lab-a", "document-review", key, Map.of());
        race(start, start);
        assertEquals(1, engine.getRuntimeService().createProcessInstanceQuery().processInstanceBusinessKey(key).count());
        assertEquals(1, engine.getHistoryService().createHistoricProcessInstanceQuery().processInstanceBusinessKey(key).count());
        assertEquals(1, jdbc.queryForObject("select count(*) from process_scope where business_key=?", Integer.class, key));
        assertEquals(1, jdbc.queryForObject("select count(*) from test_audit join process_scope on resource=id where action='process.start' and business_key=?", Integer.class, key));
    }

    @Test void completionRechecksRoleAfterClaim() {
        var process = start("document-review");
        var task = task(process);
        service(reviewer).claim("lab-a", task.id());
        var revoked = new User(reviewer.sub(), Set.of(new Membership("lab-a", REQUESTER)));
        denied(ErrorCode.FORBIDDEN, () -> service(revoked).complete("lab-a", task.id(), "review", "approved", null));
        assertEquals(task.id(), task(process).id());
        assertEquals(0, jdbc.queryForObject("select count(*) from test_audit where action='task.complete' and resource=?", Integer.class, task.id()));
        service(reviewer).complete("lab-a", task.id(), "review", "approved", null);
    }

    @Test void invalidStartInputsAndStarterRolesDoNotWriteState() {
        long before = engine.getRuntimeService().createProcessInstanceQuery().count();
        var reader = new User("reader", Set.of(new Membership("lab-a", new WorkspaceRole("reader"))));
        denied(ErrorCode.FORBIDDEN, () -> service(reader).start("lab-a", "document-review", "x", Map.of()));
        denied(ErrorCode.VALIDATION_FAILED, () -> service(requester).start("lab-a", null, "x", Map.of()));
        denied(ErrorCode.VALIDATION_FAILED, () -> service(requester).start("lab-a", "document-review", " ", Map.of()));
        denied(ErrorCode.VALIDATION_FAILED, () -> service(requester).start("lab-a", "document-review", "x".repeat(201), Map.of()));
        denied(ErrorCode.VALIDATION_FAILED, () -> service(requester).start("lab-a", "document-review", "x", null));
        denied(ErrorCode.VALIDATION_FAILED, () -> service(requester).start("lab-a", "document-review", "x", Map.of("title", 7)));
        denied(ErrorCode.VALIDATION_FAILED, () -> service(requester).start("lab-a", "document-review", "x", Map.of("title", "x".repeat(FlowableWorkflowService.MAX_VARIABLE + 1))));
        denied(ErrorCode.VALIDATION_FAILED, () -> service(requester).start("lab-a", "document-review", "x", Map.of("outcome_review", "approved")));
        assertEquals(before, engine.getRuntimeService().createProcessInstanceQuery().count());
    }

    @Test void movingResourcesPreservesDeploymentIdentityAndRunningInstanceAcrossRegistryRestart() throws Exception {
        var repository = engine.getRepositoryService();
        var strategy = new org.flowable.spring.configurator.DefaultAutoDeploymentStrategy();
        var moved = registration.policies().stream().map(p -> new org.springframework.core.io.ClassPathResource(
            "apps/samples/process/" + p.processKey() + ".bpmn20.xml")).toArray(org.springframework.core.io.Resource[]::new);
        var legacy = new org.springframework.core.io.Resource[moved.length];
        for (int i = 0; i < moved.length; i++) {
            var resource = moved[i];
            legacy[i] = new org.springframework.core.io.ByteArrayResource(resource.getContentAsByteArray()) {
                @Override public String getFilename() { return resource.getFilename(); }
                @Override public String getDescription() { return "legacy/processes/" + resource.getFilename(); }
            };
        }
        strategy.deployResources("ownership-migration-probe", legacy, engine);
        var deployment = repository.createDeploymentQuery().deploymentName("ownership-migration-probe").singleResult();
        String processId = null;
        try {
            var before = new ProcessRegistry(List.of(registration), repository);
            var oldService = new FlowableWorkflowService(engine, jdbc, requester, audit(requester), before, manager,
                vn.haui.dx.core.identity.PermissionTestDirectory.allowed());
            var process = oldService.start("lab-a", "document-review", UUID.randomUUID().toString(), Map.of());
            processId = process.processInstanceId();
            new ContentAwareDeploymentStrategy().deployResources("ownership-migration-probe", moved, engine);
            var restarted = new ProcessRegistry(List.of(registration), repository);
            assertEquals(before.definitionId("document-review"), restarted.definitionId("document-review"));
            assertEquals(1, repository.createDeploymentQuery().deploymentName("ownership-migration-probe").count());
            var newService = new FlowableWorkflowService(engine, jdbc, reviewer, audit(reviewer), restarted, manager,
                vn.haui.dx.core.identity.PermissionTestDirectory.allowed());
            var task = oldService.tasksOf("lab-a", processId).get(0);
            newService.claim("lab-a", task.id());
            newService.complete("lab-a", task.id(), "review", "approved", null);
            assertNotNull(oldService.history("lab-a", processId).endedAt());
        } finally {
            repository.deleteDeployment(deployment.getId(), true);
            if (processId != null) jdbc.update("delete from process_scope where id=?", processId);
        }
    }

    @Test void registryPinsStartsAndRejectsTaskMutationAcrossVersions() {
        var repository = engine.getRepositoryService();
        String pinned = registry.definitionId("document-review");
        var deployment = repository.createDeployment().addClasspathResource("apps/samples/process/document-review.bpmn20.xml").deploy();
        String newProcessId = null;
        try {
            var refreshed = new ProcessRegistry(List.of(registration), repository);
            assertNotEquals(pinned, refreshed.definitionId("document-review"));
            var existing = start("document-review");
            assertEquals(pinned, engine.getRuntimeService().createProcessInstanceQuery()
                .processInstanceId(existing.processInstanceId()).singleResult().getProcessDefinitionId());
            var refreshedRequester = new FlowableWorkflowService(engine, jdbc, requester, audit(requester), refreshed, manager,
                vn.haui.dx.core.identity.PermissionTestDirectory.allowed());
            var refreshedReviewer = new FlowableWorkflowService(engine, jdbc, reviewer, audit(reviewer), refreshed, manager,
                vn.haui.dx.core.identity.PermissionTestDirectory.allowed());
            var newer = refreshedRequester.start("lab-a", "document-review", UUID.randomUUID().toString(), Map.of());
            newProcessId = newer.processInstanceId();
            var newerTask = refreshedRequester.tasksOf("lab-a", newProcessId).get(0);
            denied(ErrorCode.CONFLICT, () -> service(reviewer).claim("lab-a", newerTask.id()));
            var existingTask = task(existing);
            denied(ErrorCode.CONFLICT, () -> refreshedReviewer.claim("lab-a", existingTask.id()));
            service(reviewer).claim("lab-a", existingTask.id());
            denied(ErrorCode.CONFLICT, () -> refreshedReviewer.complete("lab-a", existingTask.id(), "review", "approved", null));
            service(reviewer).complete("lab-a", existingTask.id(), "review", "approved", null);
            refreshedReviewer.claim("lab-a", newerTask.id());
            refreshedReviewer.complete("lab-a", newerTask.id(), "review", "approved", null);
            assertNotNull(refreshedRequester.history("lab-a", newProcessId).endedAt());
        } finally {
            repository.deleteDeployment(deployment.getId(), true);
            if (newProcessId != null) jdbc.update("delete from process_scope where id=?", newProcessId);
        }
    }

    @Test void legacyProbeCannotBeRegisteredOrMutatedButHistoryRemainsReadable() {
        var repository = engine.getRepositoryService();
        var deployment = repository.createDeployment().addString("legacy-document-review.bpmn20.xml", """
            <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL" xmlns:flowable="http://flowable.org/bpmn" targetNamespace="https://haui.vn/dx/core">
              <process id="document-review" name="Legacy probe" isExecutable="true">
                <startEvent id="start"/><sequenceFlow id="s1" sourceRef="start" targetRef="review"/>
                <userTask id="review" name="Review document" flowable:assignee="${initiator}"/>
                <sequenceFlow id="s2" sourceRef="review" targetRef="end"/><endEvent id="end"/>
              </process>
            </definitions>
            """).deploy();
        String legacyId = null;
        try {
            assertThrows(IllegalArgumentException.class, () -> new ProcessRegistry(List.of(registration), repository));
            var definition = repository.createProcessDefinitionQuery().deploymentId(deployment.getId()).singleResult();
            String key = UUID.randomUUID().toString();
            var legacy = engine.getRuntimeService().startProcessInstanceById(definition.getId(), key,
                Map.of("workspace", "lab-a", "initiator", requester.sub()));
            legacyId = legacy.getId();
            jdbc.update("insert into process_scope values (?,?,?,?,?)", legacyId, "lab-a", requester.sub(), "document-review", key);
            var legacyTask = service(requester).tasksOf("lab-a", legacyId).get(0);
            denied(ErrorCode.CONFLICT, () -> service(reviewer).claim("lab-a", legacyTask.id()));
            denied(ErrorCode.CONFLICT, () -> service(requester).complete("lab-a", legacyTask.id(), "review", "approved", null));
            assertEquals(requester.sub(), service(requester).history("lab-a", legacyId).initiator());
            assertEquals(legacyTask.id(), service(requester).tasksOf("lab-a", legacyId).get(0).id());
        } finally {
            repository.deleteDeployment(deployment.getId(), true);
            if (legacyId != null) jdbc.update("delete from process_scope where id=?", legacyId);
        }
    }

    private void race(Callable<?> first, Callable<?> second) throws Exception {
        var executor = Executors.newFixedThreadPool(2);
        var ready = new CountDownLatch(2);
        var go = new CountDownLatch(1);
        try {
            var futures = List.of(first, second).stream().map(action -> executor.submit(() -> {
                ready.countDown();
                assertTrue(go.await(10, TimeUnit.SECONDS));
                try { action.call(); return "ok"; }
                catch (CoreException error) { assertEquals(ErrorCode.CONFLICT, error.code()); return "conflict"; }
            })).toList();
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            go.countDown();
            var outcomes = List.of(futures.get(0).get(30, TimeUnit.SECONDS), futures.get(1).get(30, TimeUnit.SECONDS));
            assertEquals(1, outcomes.stream().filter("ok"::equals).count());
            assertEquals(1, outcomes.stream().filter("conflict"::equals).count());
        } finally { executor.shutdownNow(); }
    }

    @Test void initiatorStepIsCompletedOnlyByInitiatorWithFlowableComment() {
        var repository = engine.getRepositoryService();
        var deployment = repository.createDeployment().addClasspathResource("workflow-test/initiator-confirm.bpmn20.xml").deploy();
        try {
            ProcessRegistration confirm = () -> List.of(new ProcessPolicy("initiator-confirm", Set.of(REQUESTER), Set.of("title"),
                Map.of("review", Set.of("approved"), "confirm", Set.of("done"))));
            ProcessRegistration invalidConfirmation = new ProcessRegistration() {
                public List<ProcessPolicy> policies() { return confirm.policies(); }
                public Map<String, Set<String>> executionTasks() { return Map.of("initiator-confirm", Set.of("confirm")); }
            };
            assertThrows(IllegalArgumentException.class, () -> new ProcessRegistry(List.of(registration, invalidConfirmation), repository));
            var both = new ProcessRegistry(List.of(registration, confirm), repository);
            java.util.function.Function<User, FlowableWorkflowService> as = user -> new FlowableWorkflowService(engine, jdbc, user, audit(user), both, manager,
                vn.haui.dx.core.identity.PermissionTestDirectory.allowed());
            var process = as.apply(requester).start("lab-a", "initiator-confirm", UUID.randomUUID().toString(), Map.of("title", "Example"));
            var review = as.apply(requester).tasksOf("lab-a", process.processInstanceId()).get(0);
            as.apply(reviewer).claim("lab-a", review.id());
            // Người không giữ task không dò được tên bước: nhận 403 dù gửi sai tên bước
            denied(ErrorCode.FORBIDDEN, () -> as.apply(agent).complete("lab-a", review.id(), "wrong", "approved", null));
            as.apply(reviewer).complete("lab-a", review.id(), "review", "approved", "Looks fine");

            var step = as.apply(requester).tasksOf("lab-a", process.processInstanceId()).get(0);
            assertEquals("confirm", step.key());
            assertEquals(requester.sub(), step.assignee());
            assertTrue(step.candidateGroups().isEmpty());
            assertEquals(1, as.apply(requester).inbox("lab-a", new InboxFilter(InboxFilter.Scope.ASSIGNED), PageRequest.of(null, null)).total());
            // Bước đã giao sẵn cho người khởi tạo: người khác claim nhận 409 (đã có người giữ)
            denied(ErrorCode.CONFLICT, () -> as.apply(reviewer).claim("lab-a", step.id()));
            denied(ErrorCode.FORBIDDEN, () -> as.apply(reviewer).complete("lab-a", step.id(), "confirm", "done", null));
            denied(ErrorCode.VALIDATION_FAILED, () -> as.apply(requester).complete("lab-a", step.id(), "confirm", "approved", null));
            as.apply(requester).complete("lab-a", step.id(), "confirm", "done", "Received");

            var history = as.apply(requester).history("lab-a", process.processInstanceId());
            assertNotNull(history.endedAt());
            assertEquals(List.of("Looks fine", "Received"), history.steps().stream().map(ProcessHistory.Step::comment).toList());
            assertEquals("Received", engine.getTaskService().getTaskComments(step.id()).get(0).getFullMessage());
            denied(ErrorCode.VALIDATION_FAILED, () -> as.apply(reviewer).complete("lab-a", review.id(), "review", "approved", "x".repeat(1001)));
        } finally {
            repository.deleteDeployment(deployment.getId(), true);
        }
    }

    @Test void registryRejectsMissingDuplicateAndReservedDefinitions() {
        assertThrows(IllegalArgumentException.class, () -> new ProcessRegistry(List.of(), engine.getRepositoryService()));
        assertThrows(IllegalArgumentException.class, () -> new ProcessRegistry(List.of(registration, registration), engine.getRepositoryService()));
        ProcessRegistration reserved = () -> List.of(new ProcessPolicy("document-review", Set.of(REQUESTER), Set.of("workspace"), Map.of()));
        assertThrows(IllegalArgumentException.class, () -> new ProcessRegistry(List.of(reserved), engine.getRepositoryService()));
        ProcessRegistration missingTask = () -> registration.policies().stream()
            .map(policy -> new ProcessPolicy(policy.processKey(), policy.starterRoles(), policy.allowedStartVariables(), Map.of())).toList();
        assertThrows(IllegalArgumentException.class, () -> new ProcessRegistry(List.of(missingTask), engine.getRepositoryService()));
    }
}
