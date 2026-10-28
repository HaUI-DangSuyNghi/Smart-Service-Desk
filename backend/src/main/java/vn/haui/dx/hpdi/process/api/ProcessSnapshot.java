package vn.haui.dx.hpdi.process.api;
import java.time.Instant;
import java.util.List;
import java.util.Map;
/** Technical process projection; business classifications belong to the consuming app. */
public record ProcessSnapshot(String id, String workspace, String owner, String processKey, String businessKey,
        boolean historyPresent, Instant endedAt, String deleteReason, String endActivityId, List<TaskState> tasks, Map<String, String> outcomes) {
    public ProcessSnapshot { tasks = List.copyOf(tasks); outcomes = Map.copyOf(outcomes); }
    public ProcessSnapshot(String id, String workspace, String owner, String processKey, String businessKey,
            boolean historyPresent, Instant endedAt, String deleteReason, String endActivityId, List<TaskState> tasks) {
        this(id, workspace, owner, processKey, businessKey, historyPresent, endedAt, deleteReason, endActivityId, tasks, Map.of());
    }
    public record TaskState(String key, boolean active, String id, String workState) {
        public TaskState(String key, boolean active) { this(key, active, null, "NOT_STARTED"); }
    }
}
