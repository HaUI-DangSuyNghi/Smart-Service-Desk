package vn.haui.dx.hpdi.process.web;

import java.util.List;
import org.springframework.web.bind.annotation.*;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;
import vn.haui.dx.hpdi.common.api.*;
import vn.haui.dx.hpdi.human.api.*;
import vn.haui.dx.hpdi.process.api.*;

@RestController
@RequestMapping("/api/hpdi/v1/workspaces/{workspace}")
public class ExecutionController {
    private final WorkExecution execution;
    private final List<WorkProjection> projections;
    private final PermissionGuard permissions;
    private final HumanContext humans;

    public ExecutionController(WorkExecution execution, List<WorkProjection> projections, PermissionGuard permissions, HumanContext humans) {
        this.execution = execution;
        this.projections = List.copyOf(projections);
        this.permissions = permissions;
        this.humans = humans;
        if (projections.stream().map(WorkProjection::processKey).distinct().count() != projections.size())
            throw new IllegalArgumentException("Duplicate work projection");
    }

    public record QueuePage(List<QueueItem> items, int page, int size, long total, String assignee, long loadMinutes) {}
    public record Pause(String reason, String note) {}
    public record Release(com.fasterxml.jackson.databind.JsonNode reason, com.fasterxml.jackson.databind.JsonNode note) {}

    @PostMapping("/tasks/{id}/release")
    @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
    public void release(@PathVariable String workspace, @PathVariable String id, @RequestBody Release body) {
        permissions.require(workspace, Permissions.TASK_HANDLE);
        if (body.reason() == null || !body.reason().isTextual() || body.note() == null || !body.note().isTextual())
            throw CapabilityException.validation("String release reason and note required");
        execution.release(workspace, id, body.reason().textValue(), body.note().textValue());
    }

    @GetMapping("/queue")
    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public QueuePage queue(@PathVariable String workspace, @RequestParam(required = false) String assignee,
                           @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size) {
        String subject = assignee == null ? humans.currentActor(workspace).subject() : assignee;
        permissions.require(workspace, subject.equals(humans.currentActor(workspace).subject()) ? Permissions.TASK_HANDLE : Permissions.TASK_ASSIGN);
        var request = PageRequest.of(page, size);
        var result = execution.queue(workspace, subject, request);
        var selected = new java.util.HashMap<String, QueueItem>();
        var requestedIds = result.items().stream().map(item -> item.task().id()).collect(java.util.stream.Collectors.toSet());
        long load = 0;
        for (int index = 0; ; index++) {
            var batch = execution.queue(workspace, subject, new PageRequest(index, 100));
            for (var item : projectBatch(workspace, batch.items())) {
                load = Math.addExact(load, item.remainingMinutes());
                if (requestedIds.contains(item.task().id())) selected.put(item.task().id(),item);
            }
            if ((long) (index + 1) * 100 >= batch.total()) break;
        }
        return new QueuePage(result.items().stream().map(item -> selected.get(item.task().id())).toList(),
            result.page(), result.size(), result.total(), subject, load);
    }

    @PostMapping("/tasks/{id}/start")
    @Transactional
    public QueueItem start(@PathVariable String workspace, @PathVariable String id) {
        return change(workspace, id, WorkExecution.Action.START, null, null);
    }
    @PostMapping("/tasks/{id}/pause")
    @Transactional
    public QueueItem pause(@PathVariable String workspace, @PathVariable String id, @RequestBody Pause body) {
        return change(workspace, id, WorkExecution.Action.PAUSE, body.reason(), body.note());
    }
    @PostMapping("/tasks/{id}/resume")
    @Transactional
    public QueueItem resume(@PathVariable String workspace, @PathVariable String id) {
        return change(workspace, id, WorkExecution.Action.RESUME, null, null);
    }
    private QueueItem change(String workspace, String id, WorkExecution.Action action, String reason, String note) {
        permissions.require(workspace, Permissions.TASK_HANDLE);
        return project(workspace, execution.execute(workspace, id, action, reason, note));
    }
    private QueueItem project(String workspace, WorkExecution.Item item) {
        return projections.stream().filter(projection -> projection.processKey().equals(item.task().processKey()))
            .findFirst().orElseThrow(() -> CapabilityException.conflict("No public work projection for this process"))
            .project(workspace, item);
    }
    private List<QueueItem> projectBatch(String workspace, List<WorkExecution.Item> items) {
        var projected = new java.util.HashMap<String, QueueItem>();
        var groups = items.stream().collect(java.util.stream.Collectors.groupingBy(item -> item.task().processKey()));
        groups.forEach((key, batch) -> projections.stream().filter(provider -> provider.processKey().equals(key))
            .findFirst().orElseThrow(() -> CapabilityException.conflict("No public work projection for this process"))
            .projectBatch(workspace, batch).forEach(item -> projected.put(item.task().id(), item)));
        return items.stream().map(item -> projected.get(item.task().id())).toList();
    }
}
