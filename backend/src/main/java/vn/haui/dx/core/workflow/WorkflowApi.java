package vn.haui.dx.core.workflow;

import java.util.List;
import java.util.Map;
import java.util.Set;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.flowable.engine.HistoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import vn.haui.dx.core.audit.Audit;
import vn.haui.dx.core.identity.api.WorkspaceAccess;
import static org.springframework.http.HttpStatus.*;

@RestController
@RequestMapping("/api/core/v1/workspaces/{workspace}")
public class WorkflowApi {
    private final RuntimeService runtime;
    private final TaskService tasks;
    private final HistoryService history;
    private final JdbcTemplate jdbc;
    private final Audit audit;
    private final WorkspaceAccess access;
    public WorkflowApi(RuntimeService runtime, TaskService tasks, HistoryService history, JdbcTemplate jdbc, Audit audit,
                       WorkspaceAccess access) {
        this.runtime = runtime; this.tasks = tasks; this.history = history; this.jdbc = jdbc; this.audit = audit; this.access = access;
    }
    public record Start(@NotBlank @Size(max=200) String businessKey, @NotBlank @Size(max=200) String title) {}
    public record Complete(@NotBlank @Size(max=100) String expectedTaskKey) {}

    @PostMapping("/processes/{processKey}/instances")
    @ResponseStatus(CREATED)
    @Transactional
    public Map<String, String> start(@PathVariable String workspace, @PathVariable String processKey,
        @Valid @RequestBody Start input, @AuthenticationPrincipal Jwt jwt, @RequestAttribute String requestId) {
        // Tạm thời: mọi thành viên được khởi tạo; quyền theo starterRoles làm ở nhánh workflow
        access.requireMember(workspace);
        if (!Set.of("document-review", "equipment-approval").contains(processKey)) throw new ResponseStatusException(NOT_FOUND);
        var process = runtime.startProcessInstanceByKey(processKey, input.businessKey(),
            Map.of("initiator", jwt.getSubject(), "workspace", workspace, "title", input.title()));
        jdbc.update("insert into process_scope(id,workspace,owner,process_key,business_key) values (?,?,?,?,?)",
            process.getId(), workspace, jwt.getSubject(), processKey, input.businessKey());
        audit.record(jwt.getSubject(), "process.start", process.getId(), workspace, requestId);
        return Map.of("id", process.getId(), "processKey", processKey, "businessKey", input.businessKey(), "workspace", workspace);
    }
    private void authorize(String workspace, String id, Jwt jwt) {
        access.requireMember(workspace);
        var rows = jdbc.queryForList("select owner from process_scope where id=? and workspace=?", id, workspace);
        if (rows.isEmpty() || !jwt.getSubject().equals(rows.get(0).get("owner"))) throw new ResponseStatusException(NOT_FOUND);
    }
    @GetMapping("/instances/{id}/tasks")
    public List<Map<String, String>> tasks(@PathVariable String workspace, @PathVariable String id, @AuthenticationPrincipal Jwt jwt) {
        authorize(workspace, id, jwt);
        return tasks.createTaskQuery().processInstanceId(id).list().stream()
            .map(task -> Map.of("id", task.getId(), "key", task.getTaskDefinitionKey(), "name", task.getName())).toList();
    }
    @PostMapping("/instances/{id}/tasks/{taskId}/complete")
    @ResponseStatus(NO_CONTENT)
    @Transactional
    public void complete(@PathVariable String workspace, @PathVariable String id, @PathVariable String taskId,
        @Valid @RequestBody Complete input, @AuthenticationPrincipal Jwt jwt, @RequestAttribute String requestId) {
        authorize(workspace, id, jwt);
        jdbc.queryForList("select id from process_scope where id=? for update", id);
        var task = tasks.createTaskQuery().taskId(taskId).processInstanceId(id).singleResult();
        if (task == null || !task.getTaskDefinitionKey().equals(input.expectedTaskKey())) throw new ResponseStatusException(CONFLICT);
        if (!jwt.getSubject().equals(task.getAssignee())) throw new ResponseStatusException(FORBIDDEN);
        tasks.complete(taskId);
        audit.record(jwt.getSubject(), "task.complete", taskId, workspace, requestId);
    }
    @GetMapping("/instances/{id}/history")
    public Map<String, Object> history(@PathVariable String workspace, @PathVariable String id, @AuthenticationPrincipal Jwt jwt) {
        authorize(workspace, id, jwt);
        var process = history.createHistoricProcessInstanceQuery().processInstanceId(id).singleResult();
        var entries = history.createHistoricTaskInstanceQuery().processInstanceId(id).orderByHistoricTaskInstanceStartTime().asc().list()
            .stream().map(task -> {
                Map<String, Object> entry = new java.util.LinkedHashMap<>();
                entry.put("id", task.getId()); entry.put("key", task.getTaskDefinitionKey()); entry.put("actor", task.getAssignee());
                entry.put("startedAt", task.getStartTime()); entry.put("endedAt", task.getEndTime()); return entry;
            }).toList();
        return Map.of("id", id, "ended", process.getEndTime() != null, "tasks", entries);
    }
}
