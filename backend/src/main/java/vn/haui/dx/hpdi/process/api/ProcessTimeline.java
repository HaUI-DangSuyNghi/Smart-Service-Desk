package vn.haui.dx.hpdi.process.api;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Lịch sử một quy trình, đọc từ HistoryService của Flowable.
 *
 * @param variables nội dung người tạo nhập lúc tạo yêu cầu, theo key của startFields
 */
public record ProcessTimeline(String id, String processKey, String businessKey, String workspace, String initiator,
                             Instant startedAt, Instant endedAt, List<WorkItem> currentTasks, List<Step> steps,
                             Map<String, String> variables) {
    public ProcessTimeline {
        currentTasks = List.copyOf(currentTasks);
        steps = List.copyOf(steps);
        variables = Map.copyOf(variables);
    }

    @com.fasterxml.jackson.annotation.JsonProperty("ended")
    public boolean ended() { return endedAt != null; }

    @com.fasterxml.jackson.annotation.JsonProperty("tasks")
    public List<Step> tasks() { return steps; }

    /** Một bước; {@code outcome}, {@code comment}, {@code endedAt} là null khi chưa hoàn thành. */
    public record Step(String id, String key, String name, String assignee, String outcome, String comment,
                       Instant startedAt, Instant claimedAt, Instant endedAt) {
        @com.fasterxml.jackson.annotation.JsonProperty("actor")
        public String actor() { return assignee; }
    }
}
