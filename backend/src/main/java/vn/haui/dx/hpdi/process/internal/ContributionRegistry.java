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
    public ContributionRegistry(List<ProcessContribution> contributions) {
        policies = contributions.stream().flatMap(c -> c.definitions().stream()).map(d ->
            new ProcessPolicy(d.processKey(), d.starterRoles().stream().map(WorkspaceRole::new)
                .collect(java.util.stream.Collectors.toUnmodifiableSet()), d.allowedStartVariables(), d.outcomesByTask(),
                d.observerRoles().stream().map(WorkspaceRole::new).collect(java.util.stream.Collectors.toUnmodifiableSet()),
                d.readerRoles().stream().map(WorkspaceRole::new).collect(java.util.stream.Collectors.toUnmodifiableSet()),
                d.selfService())).toList();
    }
    public List<ProcessPolicy> policies() { return policies; }
}
