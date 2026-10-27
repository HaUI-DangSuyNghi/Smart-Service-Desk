package vn.haui.dx.hpdi.process.api;

public interface WorkEstimate {
    boolean supports(String processKey);
    record Snapshot(String generation, String label, int minutes) {}
    Snapshot current(String workspace, WorkItem task, String agent);
    void approve(String workspace, WorkItem task, String generation, int minutes, String actor, String reason);
}
