package vn.haui.dx.hpdi.process.api;

public interface WorkProjection {
    String processKey();
    QueueItem project(String workspace, WorkExecution.Item execution);
    default java.util.List<QueueItem> projectBatch(String workspace, java.util.List<WorkExecution.Item> executions) {
        return executions.stream().map(item -> project(workspace, item)).toList();
    }
}
