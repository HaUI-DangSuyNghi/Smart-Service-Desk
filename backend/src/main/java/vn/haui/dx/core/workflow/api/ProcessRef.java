package vn.haui.dx.core.workflow.api;

/** Kết quả khởi tạo quy trình. */
public record ProcessRef(String processInstanceId, String processKey, String businessKey, String workspace) {}
