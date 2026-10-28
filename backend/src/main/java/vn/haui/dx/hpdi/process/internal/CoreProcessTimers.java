package vn.haui.dx.hpdi.process.internal;

import org.springframework.stereotype.Service;
import vn.haui.dx.core.workflow.api.WorkflowTimers;
import vn.haui.dx.hpdi.process.api.ProcessTimers;

@Service
public class CoreProcessTimers implements ProcessTimers {
    private final WorkflowTimers timers;
    public CoreProcessTimers(WorkflowTimers timers) { this.timers = timers; }
    public void evaluate(String workspace, String processId, String generation) {
        timers.evaluate(workspace, processId, generation);
    }
}
