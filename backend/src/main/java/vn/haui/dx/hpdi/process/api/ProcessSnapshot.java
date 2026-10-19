package vn.haui.dx.hpdi.process.api;
import java.time.Instant;
import java.util.List;
/** Technical process projection; business classifications belong to the consuming app. */
public record ProcessSnapshot(String id, String workspace, String owner, String processKey, String businessKey,
        boolean historyPresent, Instant endedAt, String deleteReason, String endActivityId, List<TaskState> tasks) {
    public ProcessSnapshot { tasks = List.copyOf(tasks); }
    public record TaskState(String key, boolean active) {}
}
