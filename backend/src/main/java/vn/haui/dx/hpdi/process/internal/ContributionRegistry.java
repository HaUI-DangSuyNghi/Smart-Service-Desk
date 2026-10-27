package vn.haui.dx.hpdi.process.internal;
import java.util.List;
import org.springframework.stereotype.Component;
import vn.haui.dx.core.identity.api.WorkspaceRole;
import vn.haui.dx.core.workflow.api.ProcessPolicy;
import vn.haui.dx.core.workflow.api.ProcessRegistration;
import vn.haui.dx.hpdi.process.api.ProcessContribution;
@Component
public class ContributionRegistry implements ProcessRegistration {
    private final List<ProcessPolicy> policies;
    private final java.util.Map<String, java.util.Set<String>> executionTasks;
    public ContributionRegistry(List<ProcessContribution> contributions) {
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
    public java.util.Map<String, java.util.Set<String>> executionTasks() { return executionTasks; }
}
