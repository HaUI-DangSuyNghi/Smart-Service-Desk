package vn.haui.dx.hpdi.process.api;
import java.util.Map;
import vn.haui.dx.hpdi.common.api.Page;
import vn.haui.dx.hpdi.common.api.PageRequest;
/** Workspace-scoped requests and timelines with app-independent process references. */
public interface ProcessCapability {
    ProcessHandle start(String workspace, String processKey, String businessKey, Map<String, Object> variables);
    ProcessTimeline timeline(String workspace, String processInstanceId);
    /** Yêu cầu người gọi đã tạo trong workspace, mọi quy trình, mới nhất trước. */
    Page<ProcessSummary> startedByMe(String workspace, PageRequest page);
}
