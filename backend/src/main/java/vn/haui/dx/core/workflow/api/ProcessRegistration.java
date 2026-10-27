package vn.haui.dx.core.workflow.api;

import java.util.List;

/**
 * Mỗi module/app có quy trình khai báo một bean loại này. Không khởi động nếu key không có file BPMN,
 * file BPMN không được đăng ký, hoặc hai nơi đăng ký trùng key.
 */
public interface ProcessRegistration {
    default String releaseTarget(String processKey, String taskKey) { return null; }
    default void afterRelease(String workspace, String processKey, String processId, String taskId,
            String reviewId, long workedMillis, String actor, String reason, String note) {}
    default void afterExecution(String workspace, String processKey, String processId, String taskId,
            TaskExecution.Action action, String reason, java.time.Instant at) {}
    default void afterAssign(String workspace, String processKey, String processId, String taskKey, String subject) {}
    default void beforeAssign(String workspace, String processKey, String processId, String taskKey, String subject) {}
    List<ProcessPolicy> policies();

    default java.util.Map<String, java.util.Set<String>> executionTasks() { return java.util.Map.of(); }
    default void beforeComplete(String workspace, String processKey, String processId, String taskKey, String taskId, String outcome) {}
}
