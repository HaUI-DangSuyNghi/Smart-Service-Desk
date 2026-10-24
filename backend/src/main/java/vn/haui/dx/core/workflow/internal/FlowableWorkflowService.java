package vn.haui.dx.core.workflow.internal;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.flowable.engine.ProcessEngine;
import org.flowable.engine.history.HistoricProcessInstance;
import org.flowable.task.api.Task;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import vn.haui.dx.core.audit.api.AuditService;
import vn.haui.dx.core.common.CoreException;
import vn.haui.dx.core.common.Page;
import vn.haui.dx.core.common.PageRequest;
import vn.haui.dx.core.identity.api.CurrentUser;
import vn.haui.dx.core.identity.api.WorkspaceAccess;
import vn.haui.dx.core.identity.api.WorkspaceDirectory;
import vn.haui.dx.core.identity.api.WorkspaceRole;
import vn.haui.dx.core.workflow.api.*;

@Service
public final class FlowableWorkflowService implements WorkflowService {
    private final ProcessEngine engine;
    private final JdbcTemplate jdbc;
    private final Supplier<CurrentUser> current;
    private final AuditService audit;
    private final ProcessRegistry registry;
    private final TransactionTemplate transaction;
    /** Danh bạ Keycloak, để kiểm người được giao việc; null trong test không cần giao việc. */
    private final WorkspaceDirectory directory;
    static final int MAX_COMMENT = 1000;
    /** Độ dài tối đa một biến khởi tạo; ô một dòng (200) do form của Tầng 2 tự giới hạn. */
    static final int MAX_VARIABLE = 2000;
    /** Số quy trình tối đa đọc cho một lần thống kê; khoảng dài hơn nên chia nhỏ. */
    static final int MAX_STATISTICS_PROCESSES = 10_000;

    public FlowableWorkflowService(ProcessEngine engine, JdbcTemplate jdbc, CurrentUser user,
            AuditService audit, ProcessRegistry registry, PlatformTransactionManager manager) {
        this(engine, jdbc, (Supplier<CurrentUser>) () -> user, audit, registry, manager, null);
    }

    /** Cho test giao việc: người dùng cố định và danh bạ giả. */
    public FlowableWorkflowService(ProcessEngine engine, JdbcTemplate jdbc, CurrentUser user, AuditService audit,
            ProcessRegistry registry, PlatformTransactionManager manager, WorkspaceDirectory directory) {
        this(engine, jdbc, (Supplier<CurrentUser>) () -> user, audit, registry, manager, directory);
    }

    @Autowired
    public FlowableWorkflowService(ProcessEngine engine, JdbcTemplate jdbc, WorkspaceAccess access,
            AuditService audit, @org.springframework.context.annotation.Lazy ProcessRegistry registry, PlatformTransactionManager manager,
            WorkspaceDirectory directory) {
        this(engine, jdbc, access::current, audit, registry, manager, directory);
    }

    private FlowableWorkflowService(ProcessEngine engine, JdbcTemplate jdbc, Supplier<CurrentUser> current,
            AuditService audit, ProcessRegistry registry, PlatformTransactionManager manager, WorkspaceDirectory directory) {
        this.engine = engine;
        this.jdbc = jdbc;
        this.current = current;
        this.audit = audit;
        this.registry = registry;
        this.transaction = new TransactionTemplate(manager);
        this.directory = directory;
    }

    @Override
    public void requireTaskInstance(String workspace, String processInstanceId, String taskId) {
        member(workspace);
        scope(workspace, processInstanceId);
        text(taskId, 100, "taskId");
        var task = engine.getHistoryService().createHistoricTaskInstanceQuery().taskId(taskId)
            .processInstanceId(processInstanceId).singleResult();
        if (task == null) throw CoreException.notFound("Task not found in this instance");
    }

    @Override
    public ProcessRef start(String workspace, String processKey, String businessKey, Map<String, Object> variables) {
        var user = member(workspace);
        var policy = registry.require(processKey);
        if (!user.hasRole(workspace, policy.starterRoles().toArray(WorkspaceRole[]::new)))
            throw CoreException.forbidden("Starter role required");
        text(businessKey, 200, "businessKey");
        if (variables == null) throw CoreException.validation("variables are required");
        Map<String, Object> input = new HashMap<>(variables);
        input.forEach((key, value) -> {
            if (key == null || !policy.allowedStartVariables().contains(key) || !(value instanceof String))
                throw CoreException.validation("Unsupported start variable");
            text((String) value, MAX_VARIABLE, key);
        });
        input.put("workspace", workspace);
        input.put("initiator", user.sub());
        try {
            return transaction.execute(status -> {
                var process = engine.getRuntimeService().startProcessInstanceById(registry.definitionId(processKey), businessKey, input);
                jdbc.update("insert into process_scope(id,workspace,owner,process_key,business_key) values (?,?,?,?,?)",
                    process.getId(), workspace, user.sub(), processKey, businessKey);
                audit.record("process.start", "process", process.getId(), workspace,
                    Map.of("processKey", processKey, "businessKey", businessKey));
                return new ProcessRef(process.getId(), processKey, businessKey, workspace);
            });
        } catch (DuplicateKeyException exception) {
            throw CoreException.conflict("Business key already exists");
        }
    }

    @Override
    public Page<TaskView> inbox(String workspace, InboxFilter filter, PageRequest page) {
        var user = member(workspace);
        if (filter == null || page == null || page.offset() > Integer.MAX_VALUE)
            throw CoreException.validation("Invalid inbox page");
        var query = engine.getTaskService().createTaskQuery().processVariableValueEquals("workspace", workspace);
        if (filter.scope() == InboxFilter.Scope.ASSIGNED) query.taskAssignee(user.sub());
        else if (filter.scope() == InboxFilter.Scope.ALL) {
            // Xem mọi việc đang mở để giao việc: chỉ coordinator
            if (!user.hasRole(workspace, WorkspaceRole.COORDINATOR)) throw CoreException.forbidden("Coordinator required");
        } else {
            var groups = groups(user, workspace);
            if (groups.isEmpty()) return new Page<>(List.of(), page.page(), page.size(), 0);
            query.taskCandidateGroupIn(groups).taskUnassigned().processVariableValueNotEquals("initiator", user.sub());
        }
        long total = query.count();
        var items = query.orderByTaskCreateTime().asc().orderByTaskId().asc()
            .listPage((int) page.offset(), page.size()).stream().map(this::view).toList();
        return new Page<>(items, page.page(), page.size(), total);
    }

    @Override
    public List<TaskView> tasksOf(String workspace, String processInstanceId) {
        var user = member(workspace);
        readable(user, workspace, processInstanceId);
        return currentTasks(processInstanceId);
    }

    @Override
    public TaskView claim(String workspace, String taskId) {
        var user = member(workspace);
        return transaction.execute(status -> {
            var task = lockedTask(workspace, taskId);
            var scope = scope(workspace, task.getProcessInstanceId());
            if (user.sub().equals(scope.get("owner"))) throw CoreException.forbidden("Independent approver required");
            if (task.getAssignee() != null) throw CoreException.conflict("Task already claimed");
            candidate(user, workspace, task);
            engine.getTaskService().claim(taskId, user.sub());
            // Ghi kèm processKey để nhật ký hiện được tên quy trình mà không phải tra lại
            audit.record("task.claim", "task", taskId, workspace,
                Map.of("processInstanceId", task.getProcessInstanceId(), "processKey", scope.get("process_key")));
            return view(engine.getTaskService().createTaskQuery().taskId(taskId).singleResult());
        });
    }

    @Override
    public void complete(String workspace, String taskId, String expectedTaskKey, String outcome, String comment) {
        var user = member(workspace);
        text(expectedTaskKey, 100, "expectedTaskKey");
        text(outcome, 100, "outcome");
        if (comment != null && comment.length() > MAX_COMMENT) throw CoreException.validation("Comment too long");
        transaction.executeWithoutResult(status -> {
            var task = lockedTask(workspace, taskId);
            var scope = scope(workspace, task.getProcessInstanceId());
            // Kiểm người làm trước tên bước, để người không giữ task không dò được bước hiện tại qua 409
            if (!user.sub().equals(task.getAssignee())) throw CoreException.forbidden("Task assignee required");
            if (initiatorStep(task)) {
                // Bước BPMN giao cho người khởi tạo (assignee="${initiator}"), ví dụ xác nhận đã xong
                if (!user.sub().equals(scope.get("owner"))) throw CoreException.forbidden("Initiator step");
            } else {
                if (user.sub().equals(scope.get("owner"))) throw CoreException.forbidden("Independent assignee required");
                candidate(user, workspace, task);
            }
            if (!task.getTaskDefinitionKey().equals(expectedTaskKey)) throw CoreException.conflict("Unexpected task key");
            var allowed = registry.require((String) scope.get("process_key")).outcomesByTask().get(expectedTaskKey);
            if (allowed == null || !allowed.contains(outcome)) throw CoreException.validation("Invalid outcome");
            engine.getTaskService().setVariableLocal(taskId, "outcome", outcome);
            if (comment != null) {
                // Lưu bằng cơ chế comment của Flowable, người viết là người hoàn thành task
                engine.getIdentityService().setAuthenticatedUserId(user.sub());
                try { engine.getTaskService().addComment(taskId, task.getProcessInstanceId(), comment); }
                finally { engine.getIdentityService().setAuthenticatedUserId(null); }
            }
            engine.getTaskService().complete(taskId, Map.of("outcome_" + expectedTaskKey, outcome));
            audit.record("task.complete", "task", taskId, workspace,
                Map.of("taskKey", expectedTaskKey, "processInstanceId", task.getProcessInstanceId(), "outcome", outcome,
                    "processKey", scope.get("process_key")));
        });
    }

    @Override
    public ProcessHistory history(String workspace, String processInstanceId) {
        var user = member(workspace);
        var scope = readable(user, workspace, processInstanceId);
        var process = engine.getHistoryService().createHistoricProcessInstanceQuery().processInstanceId(processInstanceId).singleResult();
        var steps = engine.getHistoryService().createHistoricTaskInstanceQuery().processInstanceId(processInstanceId)
            .includeTaskLocalVariables().orderByHistoricTaskInstanceStartTime().asc().list().stream()
            .map(task -> new ProcessHistory.Step(task.getId(), task.getTaskDefinitionKey(), task.getName(), task.getAssignee(),
                (String) task.getTaskLocalVariables().get("outcome"), comment(task.getId(), task.getTaskLocalVariables()),
                instant(task.getStartTime()), instant(task.getClaimTime()), instant(task.getEndTime()))).toList();
        return new ProcessHistory(processInstanceId, (String) scope.get("process_key"), (String) scope.get("business_key"),
            workspace, (String) scope.get("owner"), instant(process.getStartTime()), instant(process.getEndTime()),
            currentTasks(processInstanceId), steps, startVariables((String) scope.get("process_key"), processInstanceId));
    }

    /** Nội dung người tạo nhập lúc start; chỉ biến khởi tạo, không lộ biến nội bộ như outcome_*. */
    private Map<String, String> startVariables(String processKey, String processInstanceId) {
        var allowed = registry.require(processKey).allowedStartVariables();
        var values = new HashMap<String, String>();
        if (allowed.isEmpty()) return values;
        engine.getHistoryService().createHistoricVariableInstanceQuery().processInstanceId(processInstanceId)
            .excludeTaskVariables().list().forEach(variable -> {
                if (allowed.contains(variable.getVariableName()) && variable.getValue() instanceof String value)
                    values.put(variable.getVariableName(), value);
            });
        return values;
    }

    @Override
    public Page<ProcessSummary> startedByMe(String workspace, PageRequest page) {
        var user = member(workspace);
        validPage(page);
        // start() luôn đặt biến workspace và initiator, nên lọc qua lịch sử biến của engine được
        return summaries(engine.getHistoryService().createHistoricProcessInstanceQuery()
            .variableValueEquals("workspace", workspace).variableValueEquals("initiator", user.sub()), page);
    }

    @Override
    public Page<ProcessSummary> readable(String workspace, PageRequest page) {
        var user = member(workspace);
        validPage(page);
        // Quy trình mà vai trò của người gọi nằm trong readerRoles (ví dụ coordinator đọc mọi ticket)
        var keys = registry.policies().stream()
            .filter(policy -> policy.readerRoles().stream().anyMatch(role -> user.hasRole(workspace, role)))
            .map(ProcessPolicy::processKey).toList();
        if (keys.isEmpty()) return new Page<>(List.of(), page.page(), page.size(), 0);
        return summaries(engine.getHistoryService().createHistoricProcessInstanceQuery()
            .variableValueEquals("workspace", workspace).processDefinitionKeyIn(keys), page);
    }

    private Page<ProcessSummary> summaries(org.flowable.engine.history.HistoricProcessInstanceQuery query, PageRequest page) {
        long total = query.count();
        var processes = query.orderByProcessInstanceStartTime().desc().orderByProcessInstanceId().asc()
            .listPage((int) page.offset(), page.size());
        var ids = processes.stream().map(HistoricProcessInstance::getId).toList();
        var active = activeTaskKeys(ids);
        var owners = owners(ids);
        var items = processes.stream().map(process -> new ProcessSummary(process.getId(),
            process.getProcessDefinitionKey(), process.getBusinessKey(), owners.get(process.getId()),
            instant(process.getStartTime()), instant(process.getEndTime()),
            active.getOrDefault(process.getId(), List.of()))).toList();
        return new Page<>(items, page.page(), page.size(), total);
    }

    /** Người tạo của nhiều quy trình trong một truy vấn (bảng process_scope của Core). */
    private Map<String, String> owners(List<String> ids) {
        if (ids.isEmpty()) return Map.of();
        String marks = String.join(",", Collections.nCopies(ids.size(), "?"));
        Map<String, String> result = new HashMap<>();
        jdbc.query("select id, owner from process_scope where id in (" + marks + ")",
            (java.sql.ResultSet row) -> { result.put(row.getString("id"), row.getString("owner")); }, ids.toArray());
        return result;
    }

    private static void validPage(PageRequest page) {
        if (page == null || page.offset() > Integer.MAX_VALUE) throw CoreException.validation("Invalid page");
    }

    @Override
    public TaskView assign(String workspace, String taskId, String assignee) {
        var user = member(workspace);
        if (!user.hasRole(workspace, WorkspaceRole.COORDINATOR)) throw CoreException.forbidden("Coordinator required");
        text(assignee, 100, "assignee");
        if (directory == null) throw CoreException.unavailable("Workspace directory is not configured");
        // Vai trò của người được giao lấy từ danh bạ Keycloak (người gọi không có token của họ)
        var target = directory.members(workspace).stream().filter(member -> member.sub().equals(assignee)).findFirst()
            .orElseThrow(() -> CoreException.validation("Assignee is not a workspace member"));
        return transaction.execute(status -> {
            var task = lockedTask(workspace, taskId);
            var scope = scope(workspace, task.getProcessInstanceId());
            var groups = view(task).candidateGroups();
            if (groups.isEmpty()) throw CoreException.conflict("Initiator step cannot be reassigned");
            if (assignee.equals(scope.get("owner"))) throw CoreException.forbidden("Independent assignee required");
            if (target.roles().stream().noneMatch(role -> groups.contains(workspace + ":" + role.value())))
                throw CoreException.validation("Assignee is not in the task's candidate group");
            String previous = task.getAssignee();
            engine.getTaskService().setAssignee(taskId, assignee);
            Map<String, Object> details = new HashMap<>();
            details.put("processInstanceId", task.getProcessInstanceId());
            details.put("taskKey", task.getTaskDefinitionKey());
            details.put("processKey", scope.get("process_key"));
            details.put("assignee", assignee);
            if (previous != null) details.put("previousAssignee", previous);
            audit.record("task.assign", "task", taskId, workspace, details);
            return view(engine.getTaskService().createTaskQuery().taskId(taskId).singleResult());
        });
    }

    @Override
    public ProcessStatistics statistics(String workspace, Instant from, Instant to, ZoneId zone) {
        var user = member(workspace);
        if (!user.hasRole(workspace, WorkspaceRole.COORDINATOR)) throw CoreException.forbidden("Coordinator required");
        if (from == null || to == null || zone == null || !from.isBefore(to) || Duration.between(from, to).toDays() > 366)
            throw CoreException.validation("Invalid statistics range");

        // Đợt quy trình bắt đầu trong khoảng; giới hạn số dòng đọc để một lần gọi không quét vô hạn
        var started = engine.getHistoryService().createHistoricProcessInstanceQuery()
            .variableValueEquals("workspace", workspace)
            .startedAfter(Date.from(from)).startedBefore(Date.from(to))
            .listPage(0, MAX_STATISTICS_PROCESSES);
        Map<String, long[]> cohort = new HashMap<>(); // key → [bắt đầu, đang chạy, hoàn thành, tổng ms hoàn thành]
        Map<LocalDate, Long> perDay = new HashMap<>();
        for (var process : started) {
            long[] counts = cohort.computeIfAbsent(process.getProcessDefinitionKey(), key -> new long[4]);
            counts[0]++;
            if (process.getEndTime() == null) counts[1]++;
            else if (process.getDeleteReason() == null) {
                counts[2]++;
                counts[3] += process.getDurationInMillis() == null ? 0 : process.getDurationInMillis();
            }
            perDay.merge(LocalDate.ofInstant(process.getStartTime().toInstant(), zone), 1L, Long::sum);
        }

        // Hiện trạng việc đang mở (không theo khoảng thời gian)
        Map<String, long[]> backlog = new HashMap<>(); // key → [chờ nhận, đang xử lý]
        Map<String, String> keyOfDefinition = new HashMap<>();
        for (Task task : engine.getTaskService().createTaskQuery().processVariableValueEquals("workspace", workspace).list()) {
            String key = keyOfDefinition.computeIfAbsent(task.getProcessDefinitionId(),
                id -> engine.getRepositoryService().getProcessDefinition(id).getKey());
            backlog.computeIfAbsent(key, k -> new long[2])[task.getAssignee() == null ? 0 : 1]++;
        }

        var keys = new java.util.TreeSet<String>();
        keys.addAll(cohort.keySet());
        keys.addAll(backlog.keySet());
        var processes = keys.stream().map(key -> {
            long[] c = cohort.getOrDefault(key, new long[4]);
            long[] b = backlog.getOrDefault(key, new long[2]);
            Long average = c[2] == 0 ? null : c[3] / c[2] / 1000;
            return new ProcessStatistics.ProcessCount(key, c[0], c[1], c[2], average, b[0], b[1]);
        }).toList();

        // Đủ mọi ngày trong khoảng (ngày không có yêu cầu = 0) để giao diện vẽ thẳng
        List<ProcessStatistics.DailyCount> daily = new ArrayList<>();
        LocalDate last = LocalDate.ofInstant(to.minusNanos(1), zone);
        for (LocalDate day = LocalDate.ofInstant(from, zone); !day.isAfter(last); day = day.plusDays(1))
            daily.add(new ProcessStatistics.DailyCount(day, perDay.getOrDefault(day, 0L)));
        return new ProcessStatistics(from, to, processes, daily);
    }

    /** Bước đang mở của nhiều quy trình trong một truy vấn, nhóm theo processInstanceId. */
    private Map<String, List<String>> activeTaskKeys(List<String> processInstanceIds) {
        if (processInstanceIds.isEmpty()) return Map.of();
        return engine.getTaskService().createTaskQuery().processInstanceIdIn(processInstanceIds)
            .orderByTaskCreateTime().asc().list().stream()
            .collect(Collectors.groupingBy(Task::getProcessInstanceId,
                Collectors.mapping(Task::getTaskDefinitionKey, Collectors.toList())));
    }

    private Task lockedTask(String workspace, String taskId) {
        text(taskId, 100, "taskId");
        var historic = engine.getHistoryService().createHistoricTaskInstanceQuery().taskId(taskId).singleResult();
        if (historic == null) throw CoreException.notFound("Task not found");
        var rows = jdbc.queryForList("select id from process_scope where id=? and workspace=? for update", historic.getProcessInstanceId(), workspace);
        if (rows.isEmpty()) throw CoreException.notFound("Task not found");
        var task = engine.getTaskService().createTaskQuery().taskId(taskId).singleResult();
        if (task == null) throw CoreException.conflict("Task already completed");
        if (!registry.supportsDefinition(task.getProcessDefinitionId()))
            throw CoreException.conflict("Unsupported process definition version; explicit migration required");
        return task;
    }

    /** Comment của bước: comment Flowable; instance cũ lưu trong biến task-local "comment". */
    private String comment(String taskId, Map<String, Object> locals) {
        var comments = engine.getTaskService().getTaskComments(taskId);
        if (!comments.isEmpty()) return comments.get(0).getFullMessage();
        return (String) locals.get("comment");
    }

    /** Bước giao thẳng cho người khởi tạo: không có candidate group (ProcessRegistry chỉ cho phép dạng này). */
    private boolean initiatorStep(Task task) { return view(task).candidateGroups().isEmpty(); }

    private Map<String, Object> readable(CurrentUser user, String workspace, String id) {
        var scope = scope(workspace, id);
        if (!user.sub().equals(scope.get("owner")) && !user.hasRole(workspace, registry.require((String) scope.get("process_key")).readerRoles().toArray(WorkspaceRole[]::new))
                && engine.getHistoryService().createHistoricTaskInstanceQuery().processInstanceId(id).taskAssignee(user.sub()).count() == 0)
            throw CoreException.notFound("Process not found");
        return scope;
    }

    private Map<String, Object> scope(String workspace, String id) {
        text(id, 100, "processInstanceId");
        var rows = jdbc.queryForList("select * from process_scope where id=? and workspace=?", id, workspace);
        if (rows.isEmpty()) throw CoreException.notFound("Process not found");
        return rows.get(0);
    }

    private List<TaskView> currentTasks(String id) {
        return engine.getTaskService().createTaskQuery().processInstanceId(id).orderByTaskCreateTime().asc()
            .list().stream().map(this::view).toList();
    }

    private TaskView view(Task task) {
        var reference = jdbc.queryForMap("select process_key,business_key from process_scope where id=?", task.getProcessInstanceId());
        var candidates = engine.getTaskService().getIdentityLinksForTask(task.getId()).stream()
            .filter(link -> "candidate".equals(link.getType()) && link.getGroupId() != null)
            .map(link -> link.getGroupId()).sorted().toList();
        return new TaskView(task.getId(), task.getProcessInstanceId(), task.getTaskDefinitionKey(), task.getName(),
            candidates, task.getAssignee(), instant(task.getCreateTime()),
            (String) reference.get("process_key"), (String) reference.get("business_key"));
    }

    private List<String> groups(CurrentUser user, String workspace) {
        return user.memberships().stream().filter(membership -> membership.workspace().equals(workspace))
            .map(membership -> membership.candidateGroup()).toList();
    }

    private void candidate(CurrentUser user, String workspace, Task task) {
        if (view(task).candidateGroups().stream().noneMatch(groups(user, workspace)::contains))
            throw CoreException.forbidden("Candidate role required");
    }

    private CurrentUser member(String workspace) {
        var user = current.get();
        if (workspace == null || !user.isMember(workspace)) throw CoreException.forbidden("Workspace membership required");
        return user;
    }

    private static void text(String value, int max, String field) {
        if (value == null || value.isBlank() || value.length() > max) throw CoreException.validation("Invalid " + field);
    }

    private static Instant instant(Date value) { return value == null ? null : value.toInstant(); }
}
