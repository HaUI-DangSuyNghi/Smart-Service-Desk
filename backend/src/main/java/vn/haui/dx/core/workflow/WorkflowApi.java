package vn.haui.dx.core.workflow;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.*;
import vn.haui.dx.core.common.CoreException;
import vn.haui.dx.core.common.Page;
import vn.haui.dx.core.common.PageRequest;
import vn.haui.dx.core.workflow.api.*;
import vn.haui.dx.core.workflow.internal.ProcessRegistry;
import static org.springframework.http.HttpStatus.*;

@RestController
@RequestMapping("/api/core/v1")
public class WorkflowApi {
    private final WorkflowService workflow;
    private final ProcessRegistry registry;
    public WorkflowApi(WorkflowService workflow, @org.springframework.context.annotation.Lazy ProcessRegistry registry) {
        this.workflow = workflow;
        this.registry = registry;
    }
    public record Start(@NotBlank @Size(max=200) String businessKey,
                        @Size(max=200) String title, Map<String, Object> variables) {}
    public record Complete(@NotBlank @Size(max=100) String expectedTaskKey,
                           @NotBlank @Size(max=100) String outcome, @Size(max=1000) String comment) {}

    @GetMapping("/process-definitions")
    public Collection<ProcessPolicy> definitions() { return registry.policies(); }

    @PostMapping("/workspaces/{workspace}/processes/{processKey}/instances")
    @ResponseStatus(CREATED)
    public ProcessRef start(@PathVariable String workspace, @PathVariable String processKey,
                            @Valid @RequestBody Start input) {
        var variables = new HashMap<String, Object>(input.variables() == null ? Map.of() : input.variables());
        if (input.title() != null && variables.putIfAbsent("title", input.title()) != null)
            throw CoreException.validation("Supply title once");
        return workflow.start(workspace, processKey, input.businessKey(), variables);
    }
    @GetMapping("/workspaces/{workspace}/tasks")
    public Page<TaskView> inbox(@PathVariable String workspace, @RequestParam(defaultValue="assigned") String scope,
                                @RequestParam(required=false) Integer page, @RequestParam(required=false) Integer size) {
        var filter = switch (scope) {
            case "assigned" -> InboxFilter.Scope.ASSIGNED;
            case "candidate" -> InboxFilter.Scope.CANDIDATE;
            default -> throw CoreException.validation("scope must be assigned or candidate");
        };
        return workflow.inbox(workspace, new InboxFilter(filter), PageRequest.of(page, size));
    }
    @GetMapping("/workspaces/{workspace}/instances/{id}/tasks")
    public List<TaskView> tasks(@PathVariable String workspace, @PathVariable String id) {
        return workflow.tasksOf(workspace, id);
    }
    @PostMapping("/workspaces/{workspace}/instances/{id}/tasks/{taskId}/claim")
    public TaskView claim(@PathVariable String workspace, @PathVariable String id, @PathVariable String taskId) {
        requireTask(workspace, id, taskId);
        return workflow.claim(workspace, taskId);
    }
    @PostMapping("/workspaces/{workspace}/instances/{id}/tasks/{taskId}/complete")
    @ResponseStatus(NO_CONTENT)
    public void complete(@PathVariable String workspace, @PathVariable String id, @PathVariable String taskId,
                         @Valid @RequestBody Complete input) {
        requireTask(workspace, id, taskId);
        workflow.complete(workspace, taskId, input.expectedTaskKey(), input.outcome(), input.comment());
    }
    private void requireTask(String workspace, String id, String taskId) {
        workflow.requireTaskInstance(workspace, id, taskId);
    }
    @GetMapping("/workspaces/{workspace}/instances/{id}/history")
    public ProcessHistory history(@PathVariable String workspace, @PathVariable String id) {
        return workflow.history(workspace, id);
    }
}
