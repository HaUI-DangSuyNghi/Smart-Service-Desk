package vn.haui.dx.hpdi.process.internal;
import java.util.List;
import org.springframework.stereotype.Component;
import vn.haui.dx.core.identity.api.WorkspaceRole;
import vn.haui.dx.core.workflow.api.ProcessPolicy;
import vn.haui.dx.core.workflow.api.ProcessRegistration;
import vn.haui.dx.hpdi.process.api.ProcessContribution;
@Component
public class ContributionRegistry implements ProcessRegistration {
    private ProcessContribution owner(String key) {
        return contributions.stream().filter(value -> value.definitions().stream()
            .anyMatch(definition -> definition.processKey().equals(key))).findFirst().orElseThrow();
    }
    private static vn.haui.dx.hpdi.process.api.WorkItem item(vn.haui.dx.core.workflow.api.TaskView task) {
        return new vn.haui.dx.hpdi.process.api.WorkItem(task.id(), task.processInstanceId(), task.key(), task.name(),
            task.candidateGroups(), task.assignee(), task.createdAt(), task.processKey(), task.businessKey());
    }
    public void afterComplete(String workspace, String key, String processId,
            vn.haui.dx.core.workflow.api.TaskView completed, String outcome,
            List<vn.haui.dx.core.workflow.api.TaskView> active) {
        owner(key).afterComplete(workspace, key, processId, item(completed), outcome,
            active.stream().map(ContributionRegistry::item).toList());
        workChanged(workspace);
    }
    public String evaluateTimer(String workspace, String key, String processId, String businessKey,
            String initiator, String generation, vn.haui.dx.core.workflow.api.TaskView active, java.time.Instant now) {
        return owner(key).evaluateTimer(workspace, key, processId, businessKey, initiator, generation, item(active), now);
    }

    @Override public void enrollTimer(String workspace, String key, String processId, String businessKey,
            String owner, String generation, java.time.Instant createdAt, String handleId, java.time.Instant resolvedAt, java.time.Instant now) {
        owner(key).enrollTimer(workspace, key, processId, businessKey, owner, generation, createdAt, handleId, resolvedAt, now);
    }
    @Override public void afterAssignmentChanged(String workspace, String taskId, String assignee, String actor) {
        estimateLifecycle.assignmentChanged(workspace, taskId, assignee, actor);
        workChanged(workspace);
    }
    public String releaseTarget(String processKey, String taskKey) {
        return contributions.stream().filter(value -> value.definitions().stream().anyMatch(definition -> definition.processKey().equals(processKey)))
            .findFirst().orElseThrow().releaseTarget(processKey, taskKey);
    }
    public void afterRelease(String workspace, String processKey, String processId, String taskId,
            String reviewId, long workedMillis, String actor, String reason, String note) {
        contributions.stream().filter(value -> value.definitions().stream().anyMatch(definition -> definition.processKey().equals(processKey)))
            .findFirst().orElseThrow().afterRelease(workspace, processKey, processId, taskId, reviewId, workedMillis, actor, reason, note);
        workChanged(workspace);
    }
    @Override public void afterExecution(String workspace, String processKey, String processId, String taskId,
            vn.haui.dx.core.workflow.api.TaskExecution.Action action, String reason, java.time.Instant at) {
        contributions.stream().filter(contribution -> contribution.definitions().stream()
            .anyMatch(definition -> definition.processKey().equals(processKey))).findFirst().orElseThrow()
            .afterExecution(workspace, processKey, processId, taskId,
                vn.haui.dx.hpdi.process.api.WorkExecution.Action.valueOf(action.name()), reason, at);
        workChanged(workspace);
    }
    @Override public void afterAssign(String workspace, String processKey, String processId, String taskKey, String subject) {
        contributions.stream().filter(contribution -> contribution.definitions().stream()
            .anyMatch(definition -> definition.processKey().equals(processKey))).findFirst().orElseThrow()
            .afterAssign(workspace, processKey, processId, taskKey, subject);
        workChanged(workspace);
    }
    private final List<ProcessPolicy> policies;
    private final List<ProcessContribution> contributions;
    private final java.util.Map<String, java.util.Set<String>> executionTasks;
    private final EstimateRequestLifecycle estimateLifecycle;
    private vn.haui.dx.hpdi.human.api.LiveUpdates live;
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
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    void setLive(vn.haui.dx.hpdi.human.api.LiveUpdates live) { this.live = live; }

    // Mọi đổi việc của mọi app đều qua đây: báo hàng đợi / việc của tôi làm mới
    private void workChanged(String workspace) {
        if (live != null) live.toChannel(workspace, vn.haui.dx.hpdi.human.api.LiveUpdates.WORK, "WORK_CHANGED", "work", null);
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
