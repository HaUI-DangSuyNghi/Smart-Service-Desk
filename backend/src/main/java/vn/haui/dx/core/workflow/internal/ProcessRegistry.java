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
    private final Map<String, String> definitionIds;

    public ProcessRegistry(Collection<ProcessRegistration> registrations, RepositoryService repository) {
        Map<String, ProcessPolicy> collected = new HashMap<>();
        for (var registration : registrations) {
            for (var policy : registration.policies()) {
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
            var outcomes = collected.get(definition.getKey()).outcomesByTask();
            if (!taskKeys.equals(outcomes.keySet()) || outcomes.values().stream().anyMatch(values ->
                    values.isEmpty() || values.stream().anyMatch(value -> value.isBlank() || value.length() > 100)))
                throw new IllegalArgumentException("Task policies and BPMN user tasks differ");
        }
        policies = Map.copyOf(collected);
        definitionIds = definitions.stream().collect(Collectors.toUnmodifiableMap(
            definition -> definition.getKey(), definition -> definition.getId()));
    }

    /** Bước duyệt: giao cho nhóm ${workspace}:role, không có assignee cố định. */
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

    public Collection<ProcessPolicy> policies() { return policies.values(); }
}
