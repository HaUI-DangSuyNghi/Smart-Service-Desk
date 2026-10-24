package vn.haui.dx.core.workflow.api;

import java.util.List;
import java.util.Map;
import vn.haui.dx.core.common.Page;
import vn.haui.dx.core.common.PageRequest;

/**
 * Thao tác quy trình cho người dùng hiện tại. Mọi hàm tự kiểm quyền, nơi gọi không tự kiểm.
 * Lỗi ném {@code CoreException}: FORBIDDEN thiếu vai trò; NOT_FOUND không tồn tại hoặc không được xem;
 * CONFLICT trùng business key, claim thua, sai bước, gửi lặp; VALIDATION_FAILED biến/outcome không hợp lệ.
 */
public interface WorkflowService {
    void requireTaskInstance(String workspace, String processInstanceId, String taskId);

    /** Khởi tạo; {@code variables} chỉ gồm biến có trong allowedStartVariables. */
    ProcessRef start(String workspace, String processKey, String businessKey, Map<String, Object> variables);

    Page<TaskView> inbox(String workspace, InboxFilter filter, PageRequest page);

    /** Task đang mở của một quy trình; ai xem được lịch sử thì xem được. */
    List<TaskView> tasksOf(String workspace, String processInstanceId);

    /** Nhận task chưa ai giữ; người gọi phải thuộc candidate group. */
    TaskView claim(String workspace, String taskId);

    /** Hoàn thành task mình đang giữ; lưu outcome vào biến {@code outcome_<taskKey>}, comment vào Flowable. */
    void complete(String workspace, String taskId, String expectedTaskKey, String outcome, String comment);

    /** Người khởi tạo, người đã tham gia và coordinator của workspace được xem. */
    ProcessHistory history(String workspace, String processInstanceId);

    /** Quy trình người gọi đã khởi tạo trong workspace, mọi processKey, mới nhất trước. */
    Page<ProcessSummary> startedByMe(String workspace, PageRequest page);
}
