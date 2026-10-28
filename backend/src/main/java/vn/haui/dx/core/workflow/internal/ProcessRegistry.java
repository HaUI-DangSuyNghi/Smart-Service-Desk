package vn.haui.dx.core.workflow.internal;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import org.flowable.bpmn.model.UserTask;
import org.flowable.engine.RepositoryService;
import vn.haui.dx.core.common.CoreException;
import vn.haui.dx.core.workflow.api.ProcessPolicy;
import vn.haui.dx.core.workflow.api.ProcessRegistration;

@Component
@org.springframework.context.annotation.Lazy
public class ProcessRegistry {
    private final Map<String, ProcessPolicy> policies;
    private final Map<String, ProcessRegistration> owners = new HashMap<>();
    private final Map<String, String> definitionIds;
    private final Map<String, Set<String>> executionTasks;

    public ProcessRegistry(Collection<ProcessRegistration> registrations, RepositoryService repository) {
        Map<String, ProcessPolicy> collected = new HashMap<>();
        Map<String, Set<String>> work = new HashMap<>();
        for (var registration : registrations) {
            var declared = registration.policies();
            registration.executionTasks().forEach((key, tasks) -> {
                var owner = declared.stream().filter(policy -> policy.processKey().equals(key)).findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Execution policy must be declared by the process owner: " + key));
                if (!owner.outcomesByTask().keySet().containsAll(tasks))
                    throw new IllegalArgumentException("Unknown execution task: " + key);
                if (work.putIfAbsent(key, Set.copyOf(tasks)) != null)
                    throw new IllegalArgumentException("Duplicate execution policy: " + key);
            });
            for (var policy : declared) {
                owners.put(policy.processKey(), registration);
                if (collected.putIfAbsent(policy.processKey(), policy) != null)
                    throw new IllegalArgumentException("Duplicate process: " + policy.processKey());
                if (policy.allowedStartVariables().stream().anyMatch(name ->
                    Set.of("initiator", "workspace").contains(name) || name.startsWith("outcome_")))
                    throw new IllegalArgumentException("Reserved start variable");
            }
        }
        var definitions = repository.createProcessDefinitionQuery().latestVersion().list();
        var deployed = definitions.stream()
            .map(definition -> definition.getKey()).collect(Collectors.toSet());
        if (!deployed.equals(collected.keySet()))
            throw new IllegalArgumentException("Registered and deployed process keys differ");
        for (var definition : definitions) {
            var tasks = repository.getBpmnModel(definition.getId()).getMainProcess()
                .findFlowElementsOfType(UserTask.class, true);
            if (tasks.stream().anyMatch(task -> !candidateTask(task) && !initiatorTask(task)))
                throw new IllegalArgumentException("User tasks must use workspace-scoped candidate roles or assignee ${initiator}");
            // Bước ${initiator} chỉ là bước phụ; phải có ít nhất một bước duyệt độc lập, chống tự duyệt
            if (tasks.stream().noneMatch(ProcessRegistry::candidateTask))
                throw new IllegalArgumentException("Process needs at least one independent candidate-group step");
            var taskKeys = tasks.stream().map(UserTask::getId).collect(Collectors.toSet());
            if (tasks.stream().anyMatch(task -> work.getOrDefault(definition.getKey(), Set.of()).contains(task.getId())
                    && !candidateTask(task)))
                throw new IllegalArgumentException("Execution tasks require independent candidates");
            var outcomes = collected.get(definition.getKey()).outcomesByTask();
            if (!taskKeys.equals(outcomes.keySet()) || outcomes.values().stream().anyMatch(values ->
                    values.isEmpty() || values.stream().anyMatch(value -> value.isBlank() || value.length() > 100)))
                throw new IllegalArgumentException("Task policies and BPMN user tasks differ");
        }
        policies = Map.copyOf(collected);
        executionTasks = Map.copyOf(work);
        definitionIds = definitions.stream().collect(Collectors.toUnmodifiableMap(
            definition -> definition.getKey(), definition -> definition.getId()));
    }

    /** Bước duyệt: giao cho nhóm ${workspace}:role, không có assignee cố định. */
    public void beforeComplete(String workspace, String processKey, String processId, String taskKey, String taskId, String outcome) {
        owners.get(processKey).beforeComplete(workspace, processKey, processId, taskKey, taskId, outcome);
    }

    public void beforeAssign(String workspace, String processKey, String processId, String taskKey, String subject) {
        owners.get(processKey).beforeAssign(workspace, processKey, processId, taskKey, subject);
    }
    public void afterAssign(String workspace, String processKey, String processId, String taskKey, String subject) {
        owners.get(processKey).afterAssign(workspace, processKey, processId, taskKey, subject);
    }
    public void afterAssignmentChanged(String workspace, String processKey, String taskId, String assignee, String actor) {
        owners.get(processKey).afterAssignmentChanged(workspace, taskId, assignee, actor);
    }

    private static boolean candidateTask(UserTask task) {
        return (task.getAssignee() == null || task.getAssignee().isBlank()) && task.getCandidateUsers().isEmpty()
            && !task.getCandidateGroups().isEmpty()
            && task.getCandidateGroups().stream().allMatch(group -> group.matches("\\$\\{workspace\\}:[a-z0-9-]{1,30}"));
    }

    /** Bước của người khởi tạo (ví dụ xác nhận đã xong): assignee đúng ${initiator}, không có nhóm. */
    private static boolean initiatorTask(UserTask task) {
        return "${initiator}".equals(task.getAssignee()) && task.getCandidateGroups().isEmpty() && task.getCandidateUsers().isEmpty();
    }

    public ProcessPolicy require(String key) {
        if (key == null || key.isBlank() || key.length() > 100) throw CoreException.validation("Invalid processKey");
        var policy = policies.get(key);
        if (policy == null) throw CoreException.notFound("Unknown process");
        return policy;
    }

    public String definitionId(String key) {
        require(key);
        return definitionIds.get(key);
    }

    public boolean supportsDefinition(String id) { return definitionIds.containsValue(id); }
    public String releaseTarget(String processKey, String taskKey) {
        return owners.get(processKey).releaseTarget(processKey, taskKey);
    }
    public void afterRelease(String workspace, String processKey, String processId, String taskId,
            String reviewId, long workedMillis, String actor, String reason, String note) {
        owners.get(processKey).afterRelease(workspace, processKey, processId, taskId, reviewId, workedMillis, actor, reason, note);
    }

    public boolean executionTask(String processKey, String taskKey) {
        return executionTasks.getOrDefault(processKey, Set.of()).contains(taskKey);
    }

    public void afterExecution(String workspace, String processKey, String processId, String taskId,
            vn.haui.dx.core.workflow.api.TaskExecution.Action action, String reason, java.time.Instant at) {
        owners.get(processKey).afterExecution(workspace, processKey, processId, taskId, action, reason, at);
    }

    public Map<String, Set<String>> executionTasks() { return executionTasks; }

    public void afterComplete(String workspace, String key, String processId,
            vn.haui.dx.core.workflow.api.TaskView completed, String outcome,
            java.util.List<vn.haui.dx.core.workflow.api.TaskView> active) {
        owners.get(key).afterComplete(workspace, key, processId, completed, outcome, active);
    }

    public String evaluateTimer(String workspace, String key, String processId, String businessKey,
            String owner, String generation, vn.haui.dx.core.workflow.api.TaskView active, java.time.Instant now) {
        return owners.get(key).evaluateTimer(workspace, key, processId, businessKey, owner, generation, active, now);
    }

    public Collection<ProcessPolicy> policies() { return policies.values(); }
}
