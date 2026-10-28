package vn.haui.dx.hpdi.process.internal;
import java.util.function.Supplier;
import org.springframework.stereotype.Service;
import vn.haui.dx.core.workflow.api.TaskScheduling;
import vn.haui.dx.hpdi.process.api.WorkScheduling;
@Service
public class CoreWorkScheduling implements WorkScheduling {
    private final TaskScheduling tasks;
    public CoreWorkScheduling(TaskScheduling tasks) { this.tasks = tasks; }
    public boolean reconcile(String workspace, String processId, String taskId, Supplier<Schedule> change) {
        return tasks.reconcile(workspace, processId, taskId, () -> {
            var value = change.get();
            return value == null ? null : new TaskScheduling.Schedule(value.priority(), value.dueAt());
        });
    }
}
