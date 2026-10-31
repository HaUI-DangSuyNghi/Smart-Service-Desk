package vn.haui.dx.hpdi.process.api;
import java.time.Instant;
import java.util.function.Supplier;
public interface WorkScheduling {
    record Schedule(int priority, Instant dueAt) {}
    boolean reconcile(String workspace, String processId, String taskId, Supplier<Schedule> change);
}
