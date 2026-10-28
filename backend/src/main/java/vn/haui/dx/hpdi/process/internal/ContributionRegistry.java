package vn.haui.dx.hpdi.process.internal;
import java.util.List;
import org.springframework.stereotype.Component;
import vn.haui.dx.core.identity.api.WorkspaceRole;
import vn.haui.dx.core.workflow.api.ProcessPolicy;
import vn.haui.dx.core.workflow.api.ProcessRegistration;
import vn.haui.dx.hpdi.process.api.ProcessContribution;
@Component
public class ContributionRegistry implements ProcessRegistration {
    @Override public void afterAssignmentChanged(String workspace, String taskId, String assignee, String actor) {
        estimateLifecycle.assignmentChanged(workspace, taskId, assignee, actor);
    }
    public String releaseTarget(String processKey, String taskKey) {
        return contributions.stream().filter(value -> value.definitions().stream().anyMatch(definition -> definition.processKey().equals(processKey)))
            .findFirst().orElseThrow().releaseTarget(processKey, taskKey);
    }
    public void afterRelease(String workspace, String processKey, String processId, String taskId,
            String reviewId, long workedMillis, String actor, String reason, String note) {
        contributions.stream().filter(value -> value.definitions().stream().anyMatch(definition -> definition.processKey().equals(processKey)))
            .findFirst().orElseThrow().afterRelease(workspace, processKey, processId, taskId, reviewId, workedMillis, actor, reason, note);
    }
    @Override public void afterExecution(String workspace, String processKey, String processId, String taskId,
            vn.haui.dx.core.workflow.api.TaskExecution.Action action, String reason, java.time.Instant at) {
        contributions.stream().filter(contribution -> contribution.definitions().stream()
            .anyMatch(definition -> definition.processKey().equals(processKey))).findFirst().orElseThrow()
            .afterExecution(workspace, processKey, processId, taskId,
                vn.haui.dx.hpdi.process.api.WorkExecution.Action.valueOf(action.name()), reason, at);
    }
    @Override public void afterAssign(String workspace, String processKey, String processId, String taskKey, String subject) {
        contributions.stream().filter(contribution -> contribution.definitions().stream()
            .anyMatch(definition -> definition.processKey().equals(processKey))).findFirst().orElseThrow()
            .afterAssign(workspace, processKey, processId, taskKey, subject);
    }
    private final List<ProcessPolicy> policies;
    private final List<ProcessContribution> contributions;
    private final java.util.Map<String, java.util.Set<String>> executionTasks;
    private final EstimateRequestLifecycle estimateLifecycle;
    public ContributionRegistry(List<ProcessContribution> contributions, EstimateRequestLifecycle estimateLifecycle) {
        this.estimateLifecycle = estimateLifecycle;
        this.contributions = List.copyOf(contributions);
        var work = new java.util.HashMap<String, java.util.Set<String>>();
        for (var contribution : contributions) {
            contribution.executionTasks().forEach((key, tasks) -> {
                var owner = contribution.definitions().stream().filter(definition -> definition.processKey().equals(key)).findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Execution policy must be declared by the process owner: " + key));
                if (!owner.outcomesByTask().keySet().containsAll(tasks))
                    throw new IllegalArgumentException("Unknown execution task: " + key);
                if (work.putIfAbsent(key, java.util.Set.copyOf(tasks)) != null)
                    throw new IllegalArgumentException("Duplicate execution policy: " + key);
            });
        }
        executionTasks = java.util.Map.copyOf(work);
        policies = contributions.stream().flatMap(c -> c.definitions().stream()).map(d ->
            new ProcessPolicy(d.processKey(), d.starterRoles().stream().map(WorkspaceRole::new)
                .collect(java.util.stream.Collectors.toUnmodifiableSet()), d.allowedStartVariables(), d.outcomesByTask(),
                d.observerRoles().stream().map(WorkspaceRole::new).collect(java.util.stream.Collectors.toUnmodifiableSet()),
                d.readerRoles().stream().map(WorkspaceRole::new).collect(java.util.stream.Collectors.toUnmodifiableSet()),
                d.selfService())).toList();
    }
    public List<ProcessPolicy> policies() { return policies; }
    public void beforeAssign(String workspace, String processKey, String processId, String taskKey, String subject) {
        contributions.stream().filter(contribution -> contribution.definitions().stream()
            .anyMatch(definition -> definition.processKey().equals(processKey))).findFirst().orElseThrow()
            .beforeAssign(workspace, processKey, processId, taskKey, subject);
    }
    public void beforeComplete(String workspace, String processKey, String processId, String taskKey, String taskId, String outcome) {
        contributions.stream().filter(contribution -> contribution.definitions().stream()
            .anyMatch(definition -> definition.processKey().equals(processKey))).findFirst().orElseThrow()
            .beforeComplete(workspace, processKey, processId, taskKey, taskId, outcome);
    }
    public java.util.Map<String, java.util.Set<String>> executionTasks() { return executionTasks; }
}
