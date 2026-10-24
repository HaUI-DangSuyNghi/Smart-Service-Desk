package vn.haui.dx.hpdi.process.api;
import vn.haui.dx.hpdi.common.api.Page;
import vn.haui.dx.hpdi.common.api.PageRequest;
public interface WorkInbox {
    /** ASSIGNED: việc tôi giữ; CANDIDATE: việc nhóm tôi chưa ai nhận; ALL: mọi việc đang mở (để giao việc). */
    enum Scope { ASSIGNED, CANDIDATE, ALL }
    Page<WorkItem> list(String workspace, Scope scope, PageRequest page);
    WorkItem claim(String workspace, String taskId);
    void complete(String workspace, String taskId, String expectedTaskKey, String outcome, String comment);
    /** Giao (lại) việc cho người thuộc nhóm xử lý của bước; không giao cho người tạo yêu cầu. */
    WorkItem assign(String workspace, String taskId, String assignee);
}
