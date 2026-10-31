package vn.haui.dx.hpdi.process.api;

import com.fasterxml.jackson.annotation.JsonUnwrapped;

public record QueueItem(@JsonUnwrapped WorkItem task, String label, String workState, String priority,
                        String basePriority, Estimate estimate, long workedMinutes, long remainingMinutes,
                        int reopenCount, String pendingReason, String pendingNote, Sla sla) {
    public QueueItem(WorkItem task, String label, String workState, String priority, String basePriority,
            Estimate estimate, long workedMinutes, long remainingMinutes, int reopenCount, String pendingReason, String pendingNote) {
        this(task, label, workState, priority, basePriority, estimate, workedMinutes, remainingMinutes,
            reopenCount, pendingReason, pendingNote, null);
    }
    /** basis: DEFAULT, COORDINATOR, SIMILAR (trung vị); sampleSize: số việc dùng tính trung vị, chỉ có khi SIMILAR. */
    public record Estimate(int minutes, String basis,
                           @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
                           Integer sampleSize) {
        public Estimate(int minutes, String basis) { this(minutes, basis, null); }
    }
}
