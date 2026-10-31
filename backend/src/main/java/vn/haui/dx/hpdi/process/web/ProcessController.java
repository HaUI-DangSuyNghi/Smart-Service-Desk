package vn.haui.dx.hpdi.process.web;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import vn.haui.dx.hpdi.common.api.CapabilityException;
import vn.haui.dx.hpdi.common.api.Page;
import vn.haui.dx.hpdi.common.api.PageRequest;
import vn.haui.dx.hpdi.human.api.PermissionGuard;
import vn.haui.dx.hpdi.human.api.Permissions;
import vn.haui.dx.hpdi.process.api.*;
// Import rõ ràng: java.lang cũng có ProcessHandle, import * không đủ để phân biệt
import vn.haui.dx.hpdi.process.api.ProcessHandle;

/**
 * API quy trình của Tầng 2. Mỗi API kiểm quyền Keycloak trước (PermissionGuard); Core vẫn tự kiểm vai trò,
 * người giữ việc, người tạo yêu cầu... nên hai lớp kiểm độc lập.
 */
@RestController
@RequestMapping("/api/hpdi/v1/workspaces/{workspace}")
public class ProcessController {
    private final ProcessCapability processes;
    private final ProcessCatalog catalog;
    private final WorkInbox inbox;
    private final PermissionGuard permissions;
    public ProcessController(ProcessCapability processes, ProcessCatalog catalog, WorkInbox inbox,
                             PermissionGuard permissions) {
        this.processes = processes; this.catalog = catalog; this.inbox = inbox; this.permissions = permissions;
    }

    /** Mọi quy trình kèm nhãn; ai cũng đọc được vì trang tiến độ, hoạt động cần nhãn tiếng Việt. */
    @GetMapping("/processes")
    public List<ProcessDescriptor> processes(@PathVariable String workspace) {
        return catalog.describe(workspace);
    }
    public record Start(Map<String, String> variables) {}
    /** Tạo yêu cầu từ form chung; chỉ quy trình tự phục vụ. */
    @PostMapping("/processes/{processKey}/instances")
    @ResponseStatus(HttpStatus.CREATED)
    public ProcessHandle start(@PathVariable String workspace, @PathVariable String processKey, @RequestBody Start body) {
        permissions.require(workspace, Permissions.REQUEST_CREATE);
        return catalog.startSelfService(workspace, processKey, body.variables());
    }
    /** Số liệu cho dashboard (điều phối viên). {@code zone}: múi giờ IANA để chia theo ngày. */
    @GetMapping("/processes/statistics")
    public ProcessStatistics statistics(@PathVariable String workspace, @RequestParam Instant from,
            @RequestParam Instant to, @RequestParam(defaultValue = "UTC") String zone) {
        permissions.require(workspace, Permissions.DASHBOARD_VIEW);
        return processes.statistics(workspace, from, to, zoneOf(zone));
    }
    /** Yêu cầu: {@code scope=mine} của tôi; {@code scope=all} mọi yêu cầu tôi được xem (điều phối viên). */
    @GetMapping("/instances")
    public Page<ProcessSummary> instances(@PathVariable String workspace,
            @RequestParam(defaultValue = "mine") String scope,
            @RequestParam(required=false) Integer page, @RequestParam(required=false) Integer size) {
        var request = PageRequest.of(page, size);
        return switch (scope) {
            case "mine" -> {
                permissions.require(workspace, Permissions.REQUEST_CREATE);
                yield processes.startedByMe(workspace, request);
            }
            case "all" -> {
                permissions.require(workspace, Permissions.REQUEST_VIEW_ALL);
                yield processes.readable(workspace, request);
            }
            default -> throw CapabilityException.validation("scope must be mine or all");
        };
    }
    @GetMapping("/inbox")
    public Page<WorkItem> inbox(@PathVariable String workspace,
            @RequestParam(defaultValue="CANDIDATE") WorkInbox.Scope scope,
            @RequestParam(required=false) Integer page, @RequestParam(required=false) Integer size) {
        // Xem mọi việc để giao việc cần task:assign; việc của mình/của nhóm cần task:handle
        permissions.require(workspace, scope == WorkInbox.Scope.ALL ? Permissions.TASK_ASSIGN : Permissions.TASK_HANDLE);
        return inbox.list(workspace, scope, PageRequest.of(page, size));
    }
    /** Ai xem được do Core quyết định theo từng quy trình (người tạo, người đã xử lý, reader). */
    @GetMapping("/instances/{id}/timeline")
    public ProcessTimeline timeline(@PathVariable String workspace, @PathVariable String id) {
        return processes.timeline(workspace, id);
    }
    @PostMapping("/tasks/{id}/claim")
    public WorkItem claim(@PathVariable String workspace, @PathVariable String id) {
        permissions.require(workspace, Permissions.TASK_HANDLE);
        return inbox.claim(workspace, id);
    }
    public record Assignment(String assignee) {}
    @PostMapping("/tasks/{id}/assign")
    public WorkItem assign(@PathVariable String workspace, @PathVariable String id, @RequestBody Assignment body) {
        permissions.require(workspace, Permissions.TASK_ASSIGN);
        return inbox.assign(workspace, id, body.assignee());
    }
    public record Completion(String expectedTaskKey, String outcome, String comment) {}
    /** Không cần quyền riêng: Core chỉ cho người đang giữ việc hoàn thành (kể cả người tạo xác nhận kết quả). */
    @PostMapping("/tasks/{id}/complete")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void complete(@PathVariable String workspace, @PathVariable String id, @RequestBody Completion body) {
        inbox.complete(workspace, id, body.expectedTaskKey(), body.outcome(), body.comment());
    }

    private static ZoneId zoneOf(String zone) {
        try {
            return ZoneId.of(zone);
        } catch (DateTimeException error) {
            throw CapabilityException.validation("Invalid zone");
        }
    }
}
