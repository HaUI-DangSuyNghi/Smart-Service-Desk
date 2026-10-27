package vn.haui.dx.core.workflow.api;

import java.util.List;

/**
 * Mỗi module/app có quy trình khai báo một bean loại này. Không khởi động nếu key không có file BPMN,
 * file BPMN không được đăng ký, hoặc hai nơi đăng ký trùng key.
 */
public interface ProcessRegistration {
    List<ProcessPolicy> policies();

    default java.util.Map<String, java.util.Set<String>> executionTasks() { return java.util.Map.of(); }
    default void beforeComplete(String workspace, String processKey, String processId, String taskKey, String taskId, String outcome) {}
}
