package vn.haui.dx.core.workflow.api;

public interface WorkflowTimers {
    void evaluate(String workspace, String processId, String generation);
}
