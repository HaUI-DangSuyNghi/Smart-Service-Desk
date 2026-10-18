package vn.haui.dx.hpdi.process.api;
import java.time.Instant;
import java.util.List;
public record WorkItem(String id, String processInstanceId, String key, String name, List<String> candidateGroups,
                       String assignee, Instant createdAt, String processKey, String businessKey) {
    public WorkItem { candidateGroups = List.copyOf(candidateGroups); }
}
