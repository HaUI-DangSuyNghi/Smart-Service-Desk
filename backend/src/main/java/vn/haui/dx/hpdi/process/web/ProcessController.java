package vn.haui.dx.hpdi.process.web;

import org.springframework.web.bind.annotation.*;
import vn.haui.dx.hpdi.common.api.Page;
import vn.haui.dx.hpdi.common.api.PageRequest;
import vn.haui.dx.hpdi.process.api.*;

@RestController
@RequestMapping("/api/hpdi/v1/workspaces/{workspace}")
public class ProcessController {
    private final ProcessCapability processes;
    private final WorkInbox inbox;
    public ProcessController(ProcessCapability processes, WorkInbox inbox) {
        this.processes = processes; this.inbox = inbox;
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
    @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
    public void complete(@PathVariable String workspace, @PathVariable String id, @RequestBody Completion body) {
        inbox.complete(workspace, id, body.expectedTaskKey(), body.outcome(), body.comment());
    }
}
