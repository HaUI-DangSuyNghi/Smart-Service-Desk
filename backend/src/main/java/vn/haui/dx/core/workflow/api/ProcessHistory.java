package vn.haui.dx.core.workflow.api;

import java.time.Instant;
import java.util.List;

/** Lịch sử một quy trình, đọc từ HistoryService của Flowable. */
public record ProcessHistory(String id, String processKey, String businessKey, String workspace, String initiator,
                             Instant startedAt, Instant endedAt, List<TaskView> currentTasks, List<Step> steps) {
    public ProcessHistory {
        currentTasks = List.copyOf(currentTasks);
        steps = List.copyOf(steps);
    }

    /** Một bước; {@code outcome}, {@code comment}, {@code endedAt} là null khi chưa hoàn thành. */
    public record Step(String key, String name, String assignee, String outcome, String comment,
                       Instant startedAt, Instant claimedAt, Instant endedAt) {}
}
