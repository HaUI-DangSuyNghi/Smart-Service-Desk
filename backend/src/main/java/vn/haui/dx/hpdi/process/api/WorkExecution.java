package vn.haui.dx.hpdi.process.api;

import java.time.Instant;
import vn.haui.dx.hpdi.common.api.Page;
import vn.haui.dx.hpdi.common.api.PageRequest;

public interface WorkExecution {
    enum Action { START, PAUSE, RESUME }
    record Item(WorkItem task, String workState, int priority, Instant dueAt, long workedMillis,
                Instant startedAt, String pendingReason, String pendingNote) {}

    Item execute(String workspace, String taskId, Action action, String reason, String note);
    Page<Item> queue(String workspace, String assignee, PageRequest page);
}
