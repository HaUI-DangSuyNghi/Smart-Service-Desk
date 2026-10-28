package vn.haui.dx.core.workflow.api;

/**
 * Giới hạn thực thi do tầng trên cấu hình (màn Cấu hình hệ thống).
 * Không có bean nào cài đặt thì Core dùng thuộc tính dx.workflow.max-in-progress.
 */
public interface ExecutionLimits {
    /** Số việc Đang làm tối đa của một người, 1..100. */
    int maxInProgress();
}
