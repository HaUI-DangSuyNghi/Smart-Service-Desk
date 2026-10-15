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
            deployment.addClasspathResource("processes/" + policy.processKey() + ".bpmn20.xml");
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
        return new FlowableWorkflowService(engine, jdbc, user, audit, registry, manager);
    }
    private ProcessRef start(String key) { return service(requester).start("lab-a", key, UUID.randomUUID().toString(), Map.of("title", "Example")); }
    private TaskView task(ProcessRef process) { return service(requester).tasksOf("lab-a", process.processInstanceId()).get(0); }
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
        denied(ErrorCode.VALIDATION_FAILED, () -> service(requester).start("lab-a", "document-review", "x", Map.of("title", "x".repeat(201))));
        denied(ErrorCode.VALIDATION_FAILED, () -> service(requester).start("lab-a", "document-review", "x", Map.of("outcome_review", "approved")));
        assertEquals(before, engine.getRuntimeService().createProcessInstanceQuery().count());
    }

    @Test void registryPinsStartsAndRejectsTaskMutationAcrossVersions() {
        var repository = engine.getRepositoryService();
        String pinned = registry.definitionId("document-review");
        var deployment = repository.createDeployment().addClasspathResource("processes/document-review.bpmn20.xml").deploy();
        String newProcessId = null;
        try {
            var refreshed = new ProcessRegistry(List.of(registration), repository);
            assertNotEquals(pinned, refreshed.definitionId("document-review"));
            var existing = start("document-review");
            assertEquals(pinned, engine.getRuntimeService().createProcessInstanceQuery()
                .processInstanceId(existing.processInstanceId()).singleResult().getProcessDefinitionId());
            var refreshedRequester = new FlowableWorkflowService(engine, jdbc, requester, audit(requester), refreshed, manager);
            var refreshedReviewer = new FlowableWorkflowService(engine, jdbc, reviewer, audit(reviewer), refreshed, manager);
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
            var both = new ProcessRegistry(List.of(registration, confirm), repository);
            java.util.function.Function<User, FlowableWorkflowService> as = user -> new FlowableWorkflowService(engine, jdbc, user, audit(user), both, manager);
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
