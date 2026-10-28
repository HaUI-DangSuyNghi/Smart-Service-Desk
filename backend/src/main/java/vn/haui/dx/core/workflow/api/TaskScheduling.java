package vn.haui.dx.core.workflow.api;
import java.time.Instant;
import java.util.function.Supplier;
public interface TaskScheduling {
    record Schedule(int priority, Instant dueAt) {
        public Schedule {
            if (priority < 0 || priority > 100 || dueAt == null) throw new IllegalArgumentException("Invalid task schedule");
        }
    }
    boolean reconcile(String workspace, String processId, String taskId, Supplier<Schedule> change);
}
