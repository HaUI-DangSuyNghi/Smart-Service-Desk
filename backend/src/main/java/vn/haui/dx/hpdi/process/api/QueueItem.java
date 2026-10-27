package vn.haui.dx.hpdi.process.api;

import com.fasterxml.jackson.annotation.JsonUnwrapped;

public record QueueItem(@JsonUnwrapped WorkItem task, String label, String workState, String priority,
                        String basePriority, Estimate estimate, long workedMinutes, long remainingMinutes,
                        int reopenCount, String pendingReason, String pendingNote) {
    public record Estimate(int minutes, String basis) {}
}
