package vn.haui.dx.core.workflow.api;

import java.time.Instant;
import java.util.List;

/**
 * Một task đang mở.
 *
 * @param key id của userTask trong BPMN, dùng làm {@code expectedTaskKey}
 * @param candidateGroups ví dụ {@code lab-a:coordinator}
 * @param assignee {@code sub} người đang giữ, {@code null} nếu chưa ai nhận
 */
public record TaskView(String id, String processInstanceId, String key, String name, List<String> candidateGroups,
                       String assignee, Instant createdAt,
                       @com.fasterxml.jackson.annotation.JsonIgnore String processKey,
                       @com.fasterxml.jackson.annotation.JsonIgnore String businessKey) {
    public TaskView { candidateGroups = List.copyOf(candidateGroups); }
}
