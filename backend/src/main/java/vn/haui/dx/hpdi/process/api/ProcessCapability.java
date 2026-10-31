package vn.haui.dx.hpdi.process.api;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Map;
import vn.haui.dx.hpdi.common.api.Page;
import vn.haui.dx.hpdi.common.api.PageRequest;
/** Workspace-scoped requests and timelines with app-independent process references. */
public interface ProcessCapability {
    ProcessHandle start(String workspace, String processKey, String businessKey, Map<String, Object> variables);
    ProcessTimeline timeline(String workspace, String processInstanceId);
    /** Yêu cầu người gọi đã tạo trong workspace, mọi quy trình, mới nhất trước. */
    Page<ProcessSummary> startedByMe(String workspace, PageRequest page);
    /** Mọi yêu cầu người gọi được xem với tư cách reader (ví dụ điều phối viên), mới nhất trước. */
    Page<ProcessSummary> readable(String workspace, PageRequest page);
    /** Số liệu dashboard trong [from, to), ngày tính theo {@code zone}. */
    ProcessStatistics statistics(String workspace, Instant from, Instant to, ZoneId zone);
}
