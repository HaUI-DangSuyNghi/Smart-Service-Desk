package vn.haui.dx.core.workflow.internal;

import java.time.Clock;
import java.util.Map;
import java.util.Objects;
import org.flowable.engine.ProcessEngine;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import vn.haui.dx.core.audit.api.AuditService;
import vn.haui.dx.core.common.CoreException;
import vn.haui.dx.core.identity.api.ServiceIdentity;
import vn.haui.dx.core.identity.api.WorkspaceAccess;
import vn.haui.dx.core.workflow.api.TaskView;
import vn.haui.dx.core.workflow.api.WorkflowTimers;

@Service
public class FlowableWorkflowTimers implements WorkflowTimers {
    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(FlowableWorkflowTimers.class);
    private final ProcessEngine engine;
    private final JdbcTemplate jdbc;
    private final ProcessRegistry registry;
    private final ServiceIdentity identity;
    private final WorkspaceAccess access;
    private final AuditService audit;
    private final TransactionTemplate transaction;
    private final Clock clock;

    public FlowableWorkflowTimers(ProcessEngine engine, JdbcTemplate jdbc, @Lazy ProcessRegistry registry,
            ServiceIdentity identity, WorkspaceAccess access, AuditService audit,
            PlatformTransactionManager manager, ObjectProvider<Clock> clocks) {
        this.engine = engine; this.jdbc = jdbc; this.registry = registry; this.identity = identity;
        this.access = access; this.audit = audit; this.transaction = new TransactionTemplate(manager);
        this.clock = clocks.getIfAvailable(Clock::systemUTC);
    }

    public void evaluate(String workspace, String processId, String generation) {
        for (var value : new String[] {workspace, processId, generation})
            if (value == null || value.isBlank() || value.length() > 100)
                throw CoreException.validation("Invalid timer correlation");
        identity.run(workspace, "workflow:timer", () -> transaction.executeWithoutResult(status -> {
            var scopes = jdbc.queryForList("select * from process_scope where workspace=? and id=? for update", workspace, processId);
            if (scopes.isEmpty()) return;
            var scope = scopes.get(0);
            var tasks = engine.getTaskService().createTaskQuery().processInstanceId(processId).list();
            if (tasks.size() != 1) return;
            var task = tasks.get(0);
            if (task.isSuspended() || !registry.supportsDefinition(task.getProcessDefinitionId())) return;
            String key = (String) scope.get("process_key");
            if (!registry.definitionId(key).equals(task.getProcessDefinitionId())) return;
            var process = engine.getRuntimeService().createProcessInstanceQuery().processInstanceId(processId).singleResult();
            if (process == null || process.isSuspended() || !Objects.equals(process.getBusinessKey(), scope.get("business_key"))) return;
            var groups = engine.getTaskService().getIdentityLinksForTask(task.getId()).stream()
                .filter(link -> "candidate".equals(link.getType()) && link.getGroupId() != null)
                .map(link -> link.getGroupId()).sorted().toList();
            var active = new TaskView(task.getId(), processId, task.getTaskDefinitionKey(), task.getName(), groups,
                task.getAssignee(), task.getCreateTime().toInstant(), key, (String) scope.get("business_key"));
            String outcome = registry.evaluateTimer(workspace, key, processId, active.businessKey(),
                (String) scope.get("owner"), generation, active, clock.instant());
            if (outcome == null) return;
            var model = engine.getRepositoryService().getBpmnModel(task.getProcessDefinitionId())
                .getMainProcess().getFlowElement(active.key());
            if (!(model instanceof org.flowable.bpmn.model.UserTask userTask)
                    || !"${initiator}".equals(userTask.getAssignee()) || !groups.isEmpty()
                    || !userTask.getCandidateUsers().isEmpty() || !userTask.getCandidateGroups().isEmpty()
                    || !Objects.equals(scope.get("owner"), task.getAssignee())
                    || !registry.require(key).outcomesByTask().getOrDefault(active.key(), java.util.Set.of()).contains(outcome))
                throw CoreException.forbidden("Timer completion is not authorized for this task");
            engine.getTaskService().setVariableLocal(task.getId(), "outcome", outcome);
            engine.getTaskService().complete(task.getId(), Map.of("outcome_" + active.key(), outcome));
            audit.recordSystem("task.timer.complete", "task", task.getId(), workspace,
                Map.of("processInstanceId", processId, "processKey", key, "generation", generation,
                    "outcome", outcome, "serviceSubject", access.current().sub()));
        }));
    }

    public void enrollConfirmations() {
        var tasks = engine.getTaskService().createTaskQuery().taskDefinitionKey("confirm").active().list();
        for (var task : tasks) {
            String processId = task.getProcessInstanceId();
            var scopes = jdbc.queryForList("select workspace,process_key from process_scope where id=?", processId);
            if (scopes.isEmpty()) continue;
            var scope = scopes.get(0);
            String workspace = (String) scope.get("workspace"), key = (String) scope.get("process_key");
            if (registry.policies().stream().noneMatch(policy -> policy.processKey().equals(key))
                    || !registry.supportsDefinition(task.getProcessDefinitionId())
                    || !registry.definitionId(key).equals(task.getProcessDefinitionId())) continue;
            try {
                identity.run(workspace, "workflow:timer", () -> transaction.executeWithoutResult(status -> {
                    var locked = jdbc.queryForList("select * from process_scope where workspace=? and id=? for update", workspace, processId);
                    if (locked.isEmpty()) return;
                    var lockedScope = locked.get(0);
                    var current = engine.getTaskService().createTaskQuery().taskId(task.getId()).singleResult();
                    if (current == null || current.isSuspended()
                            || !processId.equals(current.getProcessInstanceId())
                            || !key.equals(lockedScope.get("process_key"))
                            || engine.getTaskService().createTaskQuery().processInstanceId(processId).count() != 1) return;
                    var process = engine.getRuntimeService().createProcessInstanceQuery().processInstanceId(processId).singleResult();
                    if (process == null || process.isSuspended() || !Objects.equals(process.getBusinessKey(), lockedScope.get("business_key"))) return;
                    if (!Objects.equals(engine.getRuntimeService().getVariable(processId, "workspace"), workspace)
                            || !Objects.equals(engine.getRuntimeService().getVariable(processId, "initiator"), lockedScope.get("owner"))) return;
                    var groups = engine.getTaskService().getIdentityLinksForTask(current.getId()).stream()
                        .filter(link -> "candidate".equals(link.getType()) && link.getGroupId() != null)
                        .map(link -> link.getGroupId()).sorted().toList();
                    if (engine.getTaskService().getIdentityLinksForTask(current.getId()).stream()
                            .anyMatch(link -> "candidate".equals(link.getType()))) return;
                    var model = engine.getRepositoryService().getBpmnModel(current.getProcessDefinitionId())
                        .getMainProcess().getFlowElement(current.getTaskDefinitionKey());
                    if (!(model instanceof org.flowable.bpmn.model.UserTask userTask)
                            || !"${initiator}".equals(userTask.getAssignee()) || !groups.isEmpty()
                            || !userTask.getCandidateUsers().isEmpty() || !userTask.getCandidateGroups().isEmpty()
                            || !Objects.equals(lockedScope.get("owner"), current.getAssignee()))
                        return;
                    var history = engine.getHistoryService().createHistoricTaskInstanceQuery().processInstanceId(processId)
                        .finished().includeTaskLocalVariables().list().stream()
                        .filter(step -> step.getEndTime() != null && !step.getEndTime().after(current.getCreateTime()))
                        .max(java.util.Comparator.comparing(org.flowable.task.api.history.HistoricTaskInstance::getEndTime));
                    if (history.isEmpty() || !"resolved".equals(history.get().getTaskLocalVariables().get("outcome"))) return;
                    registry.enrollTimer(workspace, key, processId, (String) lockedScope.get("business_key"),
                        (String) lockedScope.get("owner"), current.getId(), current.getCreateTime().toInstant(), history.get().getId(), history.get().getEndTime().toInstant(), clock.instant());
                }));
            } catch (RuntimeException failure) {
                LOG.warn("Timer enrollment deferred: process={}, cause={}", processId, failure.getClass().getSimpleName());
            }
        }
    }
}
