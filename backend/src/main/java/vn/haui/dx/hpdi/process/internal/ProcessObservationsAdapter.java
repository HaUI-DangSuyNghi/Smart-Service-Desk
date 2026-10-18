package vn.haui.dx.hpdi.process.internal;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import vn.haui.dx.core.workflow.api.WorkflowObservations;
import vn.haui.dx.hpdi.human.api.HumanContext;
import vn.haui.dx.hpdi.process.api.*;
@Service
public class ProcessObservationsAdapter implements ProcessObservations {
    private final WorkflowObservations source;
    private final HumanContext human;
    public ProcessObservationsAdapter(WorkflowObservations source, HumanContext human) { this.source=source; this.human=human; }
    public List<ProcessSnapshot> snapshots(String workspace, Set<String> ids) {
        human.currentActor(workspace);
        return source.snapshots(workspace, ids).stream().map(s -> new ProcessSnapshot(s.id(), s.workspace(), s.owner(),
            s.processKey(), s.businessKey(), s.historyPresent(), s.endedAt(), s.deleteReason(), s.endActivityId(),
            s.tasks().stream().map(t -> new ProcessSnapshot.TaskState(t.key(), t.active())).toList())).toList();
    }
}
