package vn.haui.dx.hpdi.process.api;
import vn.haui.dx.hpdi.common.api.Page;
import vn.haui.dx.hpdi.common.api.PageRequest;
public interface WorkInbox {
    enum Scope { ASSIGNED, CANDIDATE }
    Page<WorkItem> list(String workspace, Scope scope, PageRequest page);
    WorkItem claim(String workspace, String taskId);
    void complete(String workspace, String taskId, String expectedTaskKey, String outcome, String comment);
}
