package vn.haui.dx.core.workflow.internal;

import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.flowable.engine.ProcessEngine;
import org.flowable.task.api.Task;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import vn.haui.dx.core.audit.api.AuditService;
import vn.haui.dx.core.common.CoreException;
import vn.haui.dx.core.common.Page;
import vn.haui.dx.core.common.PageRequest;
import vn.haui.dx.core.identity.api.CurrentUser;
import vn.haui.dx.core.identity.api.WorkspaceRole;
import vn.haui.dx.core.workflow.api.*;

public final class FlowableWorkflowService implements WorkflowService {
    private final ProcessEngine engine;
    private final JdbcTemplate jdbc;
    private final CurrentUser user;
    private final AuditService audit;
    private final ProcessRegistry registry;
    private final TransactionTemplate transaction;

    public FlowableWorkflowService(ProcessEngine engine, JdbcTemplate jdbc, CurrentUser user,
            AuditService audit, ProcessRegistry registry, PlatformTransactionManager manager) {
        this.engine = engine;
        this.jdbc = jdbc;
        this.user = user;
        this.audit = audit;
        this.registry = registry;
        this.transaction = new TransactionTemplate(manager);
    }

    @Override
    public ProcessRef start(String workspace, String processKey, String businessKey, Map<String, Object> variables) {
        member(workspace);
        var policy = registry.require(processKey);
        if (!user.hasRole(workspace, policy.starterRoles().toArray(WorkspaceRole[]::new)))
            throw CoreException.forbidden("Starter role required");
        text(businessKey, 200, "businessKey");
        if (variables == null) throw CoreException.validation("variables are required");
        Map<String, Object> input = new HashMap<>(variables);
        input.forEach((key, value) -> {
            if (key == null || !policy.allowedStartVariables().contains(key) || !(value instanceof String))
                throw CoreException.validation("Unsupported start variable");
            text((String) value, 200, key);
        });
        input.put("workspace", workspace);
        input.put("initiator", user.sub());
        try {
            return transaction.execute(status -> {
                var process = engine.getRuntimeService().startProcessInstanceById(registry.definitionId(processKey), businessKey, input);
                jdbc.update("insert into process_scope(id,workspace,owner,process_key,business_key) values (?,?,?,?,?)",
                    process.getId(), workspace, user.sub(), processKey, businessKey);
                audit.record("process.start", "process", process.getId(), workspace,
                    Map.of("processKey", processKey, "businessKey", businessKey));
                return new ProcessRef(process.getId(), processKey, businessKey, workspace);
            });
        } catch (DuplicateKeyException exception) {
            throw CoreException.conflict("Business key already exists");
        }
    }

    @Override
    public Page<TaskView> inbox(String workspace, InboxFilter filter, PageRequest page) {
        member(workspace);
        if (filter == null || page == null || page.offset() > Integer.MAX_VALUE)
            throw CoreException.validation("Invalid inbox page");
        var query = engine.getTaskService().createTaskQuery().processVariableValueEquals("workspace", workspace);
        if (filter.scope() == InboxFilter.Scope.ASSIGNED) query.taskAssignee(user.sub());
        else {
            var groups = groups(workspace);
            if (groups.isEmpty()) return new Page<>(List.of(), page.page(), page.size(), 0);
            query.taskCandidateGroupIn(groups).taskUnassigned().processVariableValueNotEquals("initiator", user.sub());
        }
        long total = query.count();
        var items = query.orderByTaskCreateTime().asc().orderByTaskId().asc()
            .listPage((int) page.offset(), page.size()).stream().map(this::view).toList();
        return new Page<>(items, page.page(), page.size(), total);
    }

    @Override
    public List<TaskView> tasksOf(String workspace, String processInstanceId) {
        readable(workspace, processInstanceId);
        return currentTasks(processInstanceId);
    }

    @Override
    public TaskView claim(String workspace, String taskId) {
        member(workspace);
        return transaction.execute(status -> {
            var task = lockedTask(workspace, taskId);
            var scope = scope(workspace, task.getProcessInstanceId());
            if (user.sub().equals(scope.get("owner"))) throw CoreException.forbidden("Independent approver required");
            if (task.getAssignee() != null) throw CoreException.conflict("Task already claimed");
            candidate(workspace, task);
            engine.getTaskService().claim(taskId, user.sub());
            audit.record("task.claim", "task", taskId, workspace, Map.of("processInstanceId", task.getProcessInstanceId()));
            return view(engine.getTaskService().createTaskQuery().taskId(taskId).singleResult());
        });
    }

    @Override
    public void complete(String workspace, String taskId, String expectedTaskKey, String outcome, String comment) {
        member(workspace);
        text(expectedTaskKey, 100, "expectedTaskKey");
        text(outcome, 100, "outcome");
        if (comment != null && comment.length() > 2000) throw CoreException.validation("Comment too long");
        transaction.executeWithoutResult(status -> {
            var task = lockedTask(workspace, taskId);
            if (!task.getTaskDefinitionKey().equals(expectedTaskKey)) throw CoreException.conflict("Unexpected task key");
            var scope = scope(workspace, task.getProcessInstanceId());
            if (!user.sub().equals(task.getAssignee()) || user.sub().equals(scope.get("owner")))
                throw CoreException.forbidden("Independent assignee required");
            candidate(workspace, task);
            var allowed = registry.require((String) scope.get("process_key")).outcomesByTask().get(expectedTaskKey);
            if (allowed == null || !allowed.contains(outcome)) throw CoreException.validation("Invalid outcome");
            Map<String, Object> local = new HashMap<>();
            local.put("outcome", outcome);
            if (comment != null) local.put("comment", comment);
            engine.getTaskService().setVariablesLocal(taskId, local);
            engine.getTaskService().complete(taskId, Map.of("outcome_" + expectedTaskKey, outcome));
            audit.record("task.complete", "task", taskId, workspace,
                Map.of("taskKey", expectedTaskKey, "processInstanceId", task.getProcessInstanceId(), "outcome", outcome));
        });
    }

    @Override
    public ProcessHistory history(String workspace, String processInstanceId) {
        var scope = readable(workspace, processInstanceId);
        var process = engine.getHistoryService().createHistoricProcessInstanceQuery().processInstanceId(processInstanceId).singleResult();
        var steps = engine.getHistoryService().createHistoricTaskInstanceQuery().processInstanceId(processInstanceId)
            .includeTaskLocalVariables().orderByHistoricTaskInstanceStartTime().asc().list().stream()
            .map(task -> new ProcessHistory.Step(task.getTaskDefinitionKey(), task.getName(), task.getAssignee(),
                (String) task.getTaskLocalVariables().get("outcome"), (String) task.getTaskLocalVariables().get("comment"),
                instant(task.getStartTime()), instant(task.getClaimTime()), instant(task.getEndTime()))).toList();
        return new ProcessHistory(processInstanceId, (String) scope.get("process_key"), (String) scope.get("business_key"),
            workspace, (String) scope.get("owner"), instant(process.getStartTime()), instant(process.getEndTime()),
            currentTasks(processInstanceId), steps);
    }

    private Task lockedTask(String workspace, String taskId) {
        text(taskId, 100, "taskId");
        var historic = engine.getHistoryService().createHistoricTaskInstanceQuery().taskId(taskId).singleResult();
        if (historic == null) throw CoreException.notFound("Task not found");
        var rows = jdbc.queryForList("select id from process_scope where id=? and workspace=? for update", historic.getProcessInstanceId(), workspace);
        if (rows.isEmpty()) throw CoreException.notFound("Task not found");
        var task = engine.getTaskService().createTaskQuery().taskId(taskId).singleResult();
        if (task == null) throw CoreException.conflict("Task already completed");
        if (!registry.supportsDefinition(task.getProcessDefinitionId()))
            throw CoreException.conflict("Unsupported process definition version; explicit migration required");
        return task;
    }

    private Map<String, Object> readable(String workspace, String id) {
        member(workspace);
        var scope = scope(workspace, id);
        if (!user.sub().equals(scope.get("owner")) && !user.hasRole(workspace, WorkspaceRole.COORDINATOR)
                && engine.getHistoryService().createHistoricTaskInstanceQuery().processInstanceId(id).taskAssignee(user.sub()).count() == 0)
            throw CoreException.notFound("Process not found");
        return scope;
    }

    private Map<String, Object> scope(String workspace, String id) {
        text(id, 100, "processInstanceId");
        var rows = jdbc.queryForList("select * from process_scope where id=? and workspace=?", id, workspace);
        if (rows.isEmpty()) throw CoreException.notFound("Process not found");
        return rows.get(0);
    }

    private List<TaskView> currentTasks(String id) {
        return engine.getTaskService().createTaskQuery().processInstanceId(id).orderByTaskCreateTime().asc()
            .list().stream().map(this::view).toList();
    }

    private TaskView view(Task task) {
        var candidates = engine.getTaskService().getIdentityLinksForTask(task.getId()).stream()
            .filter(link -> "candidate".equals(link.getType()) && link.getGroupId() != null)
            .map(link -> link.getGroupId()).sorted().toList();
        return new TaskView(task.getId(), task.getProcessInstanceId(), task.getTaskDefinitionKey(), task.getName(),
            candidates, task.getAssignee(), instant(task.getCreateTime()));
    }

    private List<String> groups(String workspace) {
        return user.memberships().stream().filter(membership -> membership.workspace().equals(workspace))
            .map(membership -> membership.candidateGroup()).toList();
    }

    private void candidate(String workspace, Task task) {
        if (view(task).candidateGroups().stream().noneMatch(groups(workspace)::contains))
            throw CoreException.forbidden("Candidate role required");
    }

    private void member(String workspace) {
        if (workspace == null || !user.isMember(workspace)) throw CoreException.forbidden("Workspace membership required");
    }

    private static void text(String value, int max, String field) {
        if (value == null || value.isBlank() || value.length() > max) throw CoreException.validation("Invalid " + field);
    }

    private static Instant instant(Date value) { return value == null ? null : value.toInstant(); }
}
