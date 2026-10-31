package vn.haui.dx.core.workflow.api;

import java.time.Instant;

public record TaskExecution(TaskView task, State state, int priority, Instant dueAt,
                            long workedMillis, Instant startedAt, String pendingReason, String pendingNote) {
    public enum State { NOT_STARTED, IN_PROGRESS, PENDING }
    public enum Action { START, PAUSE, RESUME }
}
