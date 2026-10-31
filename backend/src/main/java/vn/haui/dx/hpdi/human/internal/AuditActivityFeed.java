package vn.haui.dx.hpdi.human.internal;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import vn.haui.dx.core.audit.api.AuditEntry;
import vn.haui.dx.core.audit.api.AuditQuery;
import vn.haui.dx.core.audit.api.AuditService;
import vn.haui.dx.hpdi.common.api.Page;
import vn.haui.dx.hpdi.common.api.PageRequest;
import vn.haui.dx.hpdi.human.api.ActivityEntry;
import vn.haui.dx.hpdi.human.api.ActivityFeed;
import vn.haui.dx.hpdi.process.api.ProcessObservations;
import vn.haui.dx.hpdi.process.api.ProcessSnapshot;

/** "Hoạt động của tôi" dựng từ audit của Core, bổ sung processKey để giao diện viết được câu dễ hiểu. */
@Service
public class AuditActivityFeed implements ActivityFeed {
    private final AuditService audit;
    private final ProcessObservations processes;

    public AuditActivityFeed(AuditService audit, ProcessObservations processes) {
        this.audit = audit;
        this.processes = processes;
    }

    @Override
    public Page<ActivityEntry> mine(String workspace, PageRequest page) {
        // Core tự kiểm thành viên workspace và luôn lọc theo người gọi lấy từ token
        return entries(workspace, audit.mine(workspace, new AuditQuery(null, null, null, null), corePage(page)));
    }

    @Override
    public Page<ActivityEntry> workspace(String workspace, Instant from, Instant to, String action, String actor,
                                         PageRequest page) {
        // Core kiểm quyền đọc audit chung (điều phối viên hoặc quản trị nền tảng)
        return entries(workspace, audit.search(workspace, new AuditQuery(from, to, action, actor), corePage(page)));
    }

    private static vn.haui.dx.core.common.PageRequest corePage(PageRequest page) {
        return new vn.haui.dx.core.common.PageRequest(page.page(), page.size());
    }

    private Page<ActivityEntry> entries(String workspace, vn.haui.dx.core.common.Page<AuditEntry> result) {
        var processKeys = processKeys(workspace, result.items());
        var items = result.items().stream().map(entry -> {
            String instance = processInstanceId(entry);
            String key = text(entry.details().get("processKey"));
            // Map.of() ném lỗi khi tra key null, nên chỉ tra khi thao tác thuộc quy trình
            if (key == null && instance != null) key = processKeys.get(instance);
            return new ActivityEntry(entry.id().toString(), entry.occurredAt(), entry.actor(), entry.action(), entry.resourceType(),
                entry.resource(), instance, key, strings(entry.details()));
        }).toList();
        return new Page<>(items, result.page(), result.size(), result.total());
    }

    /** process.start ghi id quy trình ở resource; task.* ghi ở details.processInstanceId. */
    private static String processInstanceId(AuditEntry entry) {
        if ("process".equals(entry.resourceType())) return entry.resource();
        return text(entry.details().get("processInstanceId"));
    }

    // Tra processKey của cả trang trong một lần gọi; quy trình người gọi không còn được xem thì bỏ qua
    private Map<String, String> processKeys(String workspace, List<AuditEntry> entries) {
        var ids = entries.stream().map(AuditActivityFeed::processInstanceId).filter(Objects::nonNull)
            .collect(Collectors.toSet());
        if (ids.isEmpty()) return Map.of();
        return processes.snapshots(workspace, ids).stream()
            .collect(Collectors.toMap(ProcessSnapshot::id, ProcessSnapshot::processKey, (first, second) -> first));
    }

    private static Map<String, String> strings(Map<String, Object> details) {
        var result = new HashMap<String, String>();
        details.forEach((key, value) -> { if (value != null) result.put(key, value.toString()); });
        return result;
    }

    private static String text(Object value) { return value == null ? null : value.toString(); }
}
