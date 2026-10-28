package vn.haui.dx.hpdi.process.api;

import java.time.Instant;
import vn.haui.dx.hpdi.common.api.Page;
import vn.haui.dx.hpdi.common.api.PageRequest;

public interface WorkExecution {
    default void returnForCorrection(String workspace, String taskId, String note) {
        throw vn.haui.dx.hpdi.common.api.CapabilityException.conflict("Correction return is not supported");
    }
    default void release(String workspace, String taskId, String reason, String note) {
        throw vn.haui.dx.hpdi.common.api.CapabilityException.conflict("Work release is not supported");
    }
    default Page<Item> workspaceQueue(String workspace, PageRequest page) {
        throw vn.haui.dx.hpdi.common.api.CapabilityException.conflict("Workspace queue is not supported");
    }
    default Item get(String workspace, String taskId) {
        throw vn.haui.dx.hpdi.common.api.CapabilityException.conflict("Task execution is not supported");
    }
    enum Action { START, PAUSE, RESUME }
    record Item(WorkItem task, String workState, int priority, Instant dueAt, long workedMillis,
                Instant startedAt, String pendingReason, String pendingNote) {}

    Item execute(String workspace, String taskId, Action action, String reason, String note);
    Page<Item> queue(String workspace, String assignee, PageRequest page);
}
