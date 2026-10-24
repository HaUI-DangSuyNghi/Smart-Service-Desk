package vn.haui.dx.hpdi.process.web;

import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import vn.haui.dx.hpdi.common.api.Page;
import vn.haui.dx.hpdi.common.api.PageRequest;
import vn.haui.dx.hpdi.process.api.*;
// Import rõ ràng: java.lang cũng có ProcessHandle, import * không đủ để phân biệt
import vn.haui.dx.hpdi.process.api.ProcessHandle;

@RestController
@RequestMapping("/api/hpdi/v1/workspaces/{workspace}")
public class ProcessController {
    private final ProcessCapability processes;
    private final ProcessCatalog catalog;
    private final WorkInbox inbox;
    public ProcessController(ProcessCapability processes, ProcessCatalog catalog, WorkInbox inbox) {
        this.processes = processes; this.catalog = catalog; this.inbox = inbox;
    }
    /** Mọi quy trình kèm nhãn, kết quả theo bước và canStart của người gọi. */
    @GetMapping("/processes")
    public List<ProcessDescriptor> processes(@PathVariable String workspace) {
        return catalog.describe(workspace);
    }
    public record Start(Map<String, String> variables) {}
    /** Tạo yêu cầu từ form chung; chỉ quy trình tự phục vụ. */
    @PostMapping("/processes/{processKey}/instances")
    @ResponseStatus(HttpStatus.CREATED)
    public ProcessHandle start(@PathVariable String workspace, @PathVariable String processKey, @RequestBody Start body) {
        return catalog.startSelfService(workspace, processKey, body.variables());
    }
    /** Yêu cầu người gọi đã tạo, mọi quy trình. */
    @GetMapping("/instances")
    public Page<ProcessSummary> mine(@PathVariable String workspace,
            @RequestParam(required=false) Integer page, @RequestParam(required=false) Integer size) {
        return processes.startedByMe(workspace, PageRequest.of(page, size));
    }
    @GetMapping("/inbox")
    public Page<WorkItem> inbox(@PathVariable String workspace,
            @RequestParam(defaultValue="CANDIDATE") WorkInbox.Scope scope,
            @RequestParam(required=false) Integer page, @RequestParam(required=false) Integer size) {
        return inbox.list(workspace, scope, PageRequest.of(page, size));
    }
    @GetMapping("/instances/{id}/timeline")
    public ProcessTimeline timeline(@PathVariable String workspace, @PathVariable String id) {
        return processes.timeline(workspace, id);
    }
    @PostMapping("/tasks/{id}/claim")
    public WorkItem claim(@PathVariable String workspace, @PathVariable String id) {
        return inbox.claim(workspace, id);
    }
    public record Completion(String expectedTaskKey, String outcome, String comment) {}
    @PostMapping("/tasks/{id}/complete")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void complete(@PathVariable String workspace, @PathVariable String id, @RequestBody Completion body) {
        inbox.complete(workspace, id, body.expectedTaskKey(), body.outcome(), body.comment());
    }
}
