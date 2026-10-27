package vn.haui.dx.hpdi.process.api;
import java.util.List;
/** App-owned definitions; the capability validates and registers them with the engine. */
public interface ProcessContribution {
    default void afterExecution(String workspace, String processKey, String processId, String taskId,
            WorkExecution.Action action, String reason, java.time.Instant at) {}
    default void afterAssign(String workspace, String processKey, String processId, String taskKey, String subject) {}
    default void beforeAssign(String workspace, String processKey, String processId, String taskKey, String subject) {}
    List<ProcessDefinition> definitions();
    default java.util.Map<String, java.util.Set<String>> executionTasks() { return java.util.Map.of(); }
    default void beforeComplete(String workspace, String processKey, String processId, String taskKey, String taskId, String outcome) {}
}
