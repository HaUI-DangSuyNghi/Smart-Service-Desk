package vn.haui.dx.core.workflow.api;

import java.util.List;

/**
 * Mỗi module/app có quy trình khai báo một bean loại này. Không khởi động nếu key không có file BPMN,
 * file BPMN không được đăng ký, hoặc hai nơi đăng ký trùng key.
 */
public interface ProcessRegistration {
    List<ProcessPolicy> policies();
}
