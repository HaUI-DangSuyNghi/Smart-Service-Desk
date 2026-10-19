package vn.haui.dx.hpdi.process.internal;

import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import vn.haui.dx.core.workflow.api.WorkflowObservations;
import vn.haui.dx.hpdi.process.api.ProcessObservations;
import vn.haui.dx.hpdi.process.api.ProcessSnapshot;

@Service
public class ProcessObservationsAdapter implements ProcessObservations {
    private final WorkflowObservations source;
    public ProcessObservationsAdapter(WorkflowObservations source) { this.source = source; }
    public List<ProcessSnapshot> snapshots(String workspace, Set<String> ids) {
        return source.snapshots(workspace, ids).stream().map(s -> new ProcessSnapshot(s.id(), s.workspace(), s.owner(),
            s.processKey(), s.businessKey(), s.historyPresent(), s.endedAt(), s.deleteReason(), s.endActivityId(),
            s.tasks().stream().map(t -> new ProcessSnapshot.TaskState(t.key(), t.active())).toList())).toList();
    }
}
