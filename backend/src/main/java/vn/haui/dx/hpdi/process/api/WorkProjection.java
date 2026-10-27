package vn.haui.dx.hpdi.process.api;

public interface WorkProjection {
    String processKey();
    QueueItem project(String workspace, WorkExecution.Item execution);
}
