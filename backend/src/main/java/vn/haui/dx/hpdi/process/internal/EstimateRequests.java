package vn.haui.dx.hpdi.process.internal;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.haui.dx.hpdi.common.api.*;
import vn.haui.dx.hpdi.human.api.*;
import vn.haui.dx.hpdi.process.api.*;

@Service
public class EstimateRequests {
    public enum Status { PENDING, APPROVED, REJECTED, SUPERSEDED }
    public record Request(UUID id, String taskId, String processInstanceId, String businessKey, String label,
            String requestedBy, int currentMinutes, int proposedMinutes, String reason, Status status,
            Instant createdAt, String decidedBy, Instant decidedAt, String decisionNote,
            String supersededBy, Instant supersededAt, String supersededReason) {}
    private final JdbcTemplate jdbc;
    private final WorkExecution execution;
    private final WorkScheduling scheduling;
    private final List<WorkEstimate> estimates;
    private final HumanContext humans;
    private final PermissionGuard permissions;
    private final ActivityJournal journal;
    private final EstimateRequestLifecycle lifecycle;
    private final org.springframework.beans.factory.ObjectProvider<Notifications> notifications;

    public EstimateRequests(JdbcTemplate jdbc, WorkExecution execution, WorkScheduling scheduling,
            List<WorkEstimate> estimates, HumanContext humans, PermissionGuard permissions, ActivityJournal journal,
            EstimateRequestLifecycle lifecycle, org.springframework.beans.factory.ObjectProvider<Notifications> notifications) {
        this.jdbc = jdbc; this.execution = execution; this.scheduling = scheduling;
        this.estimates = List.copyOf(estimates); this.humans = humans; this.permissions = permissions; this.journal = journal;
        this.lifecycle = lifecycle; this.notifications = notifications;
    }

    private vn.haui.dx.hpdi.human.api.LiveUpdates live;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    void setLive(vn.haui.dx.hpdi.human.api.LiveUpdates live) { this.live = live; }

    // Phát sau commit nên gọi ở đầu hàm cũng được: lỗi thì rollback, không phát
    private void workChanged(String workspace) {
        if (live != null) live.toChannel(workspace, vn.haui.dx.hpdi.human.api.LiveUpdates.WORK, "WORK_CHANGED", "work", null);
    }

    @Transactional
    public Request create(String workspace, String taskId, Integer proposedMinutes, String reason) {
        workChanged(workspace);
        if (proposedMinutes == null || proposedMinutes < 1 || proposedMinutes > 10080
                || reason == null || reason.isBlank() || reason.length() > 1000)
            throw CapabilityException.validation("Valid proposedMinutes and reason required");
        var actor = humans.currentMember(workspace);
        if (!actor.hasRole("agent")) throw CapabilityException.forbidden("Agent required");
        permissions.require(workspace, Permissions.TASK_HANDLE);
        var initial = execution.get(workspace, taskId).task();
        var id = UUID.randomUUID();
        boolean active = scheduling.reconcile(workspace, initial.processInstanceId(), taskId, () -> {
            if (!humans.currentMember(workspace).hasRole("agent")) throw CapabilityException.forbidden("Current agent required");
            var task = execution.get(workspace, taskId).task();
            if (!actor.subject().equals(task.assignee())) throw CapabilityException.forbidden("Task assignee required");
            var snapshot = provider(task).current(workspace, task, actor.subject());
            lifecycle.assignmentChanged(workspace, taskId, task.assignee(), actor.subject());
            if (Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from hpdi_estimate_request where workspace=? and task_id=? and status='PENDING')",
                    Boolean.class, workspace, taskId))) throw CapabilityException.conflict("Pending estimate request already exists");
            jdbc.update("insert into hpdi_estimate_request(id,workspace,process_instance_id,task_id,generation,business_key,label,requested_by,current_minutes,proposed_minutes,reason)"
                + " values(?,?,?,?,?,?,?,?,?,?,?)", id, workspace, task.processInstanceId(), taskId, snapshot.generation(),
                task.businessKey(), snapshot.label(), actor.subject(), snapshot.minutes(), proposedMinutes, reason);
            journal.record(workspace, new BusinessActivity("estimate.request", "process.task", taskId, Map.of("requestId", id.toString())));
            // Điều phối viên duyệt ở trang Sửa ước tính (chỉ trong app)
            notifications.getObject().notify(new Notifications.Notice(workspace, "ESTIMATE_REQUESTED", Notifications.Severity.INFO,
                "Yêu cầu sửa ước tính: " + snapshot.minutes() + " → " + proposedMinutes + " phút", snapshot.label(), "/estimate-requests",
                task.processKey(), task.businessKey(), actor.subject(), Set.of(), true, false));
            return null;
        });
        if (!active) throw CapabilityException.conflict("Active work required");
        return find(workspace, id);
    }

    @Transactional(readOnly = true, isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public Page<Request> list(String workspace, Status status, PageRequest page) {
        var actor = humans.currentMember(workspace);
        boolean coordinator = actor.hasRole("coordinator");
        if (!coordinator && !actor.hasRole("agent")) throw CapabilityException.forbidden("Agent or coordinator required");
        permissions.require(workspace, coordinator ? Permissions.TASK_ASSIGN : Permissions.TASK_HANDLE);
        String where = " where workspace=?";
        var parameters = new ArrayList<Object>();
        parameters.add(workspace);
        if (!coordinator) { where += " and requested_by=?"; parameters.add(actor.subject()); }
        if (status != null) { where += " and status=?"; parameters.add(status.name()); }
        long total = jdbc.queryForObject("select count(*) from hpdi_estimate_request" + where, Long.class, parameters.toArray());
        parameters.add(page.size()); parameters.add(page.offset());
        var items = jdbc.query("select * from hpdi_estimate_request" + where + " order by created_at desc,id desc limit ? offset ?", this::map, parameters.toArray());
        return new Page<>(items, page.page(), page.size(), total);
    }

    @Transactional
    public Request decide(String workspace, UUID id, Boolean approved, String note) {
        workChanged(workspace);
        if (approved == null || (note != null && note.length() > 1000)) throw CapabilityException.validation("Invalid estimate decision");
        var actor = humans.currentMember(workspace);
        if (!actor.hasRole("coordinator")) throw CapabilityException.forbidden("Coordinator required");
        permissions.require(workspace, Permissions.TASK_ASSIGN);
        var initial = find(workspace, id);
        boolean active = scheduling.reconcile(workspace, initial.processInstanceId(), initial.taskId(), () -> {
            if (!humans.currentMember(workspace).hasRole("coordinator")) throw CapabilityException.forbidden("Current coordinator required");
            var request = find(workspace, id);
            if (request.status() != Status.PENDING) throw CapabilityException.conflict("Estimate request already decided");
            var task = execution.get(workspace, request.taskId()).task();
            if (!request.requestedBy().equals(task.assignee())) throw CapabilityException.conflict("Task assignment changed");
            var provider = provider(task);
            var snapshot = provider.current(workspace, task, request.requestedBy());
            String generation = jdbc.queryForObject("select generation from hpdi_estimate_request where workspace=? and id=?", String.class, workspace, id);
            if (!snapshot.generation().equals(generation) || snapshot.minutes() != request.currentMinutes())
                throw CapabilityException.conflict("Estimate request is stale");
            if (approved) provider.approve(workspace, task, generation, request.proposedMinutes(), actor.subject(), request.reason());
            jdbc.update("update hpdi_estimate_request set status=?,decided_by=?,decided_at=clock_timestamp(),decision_note=? where workspace=? and id=?",
                approved ? "APPROVED" : "REJECTED", actor.subject(), note, workspace, id);
            journal.record(workspace, new BusinessActivity("estimate.decision", "process.task", task.id(),
                Map.of("requestId", id.toString(), "decisionKey", approved ? "APPROVED" : "REJECTED")));
            // Người xin biết kết quả; link về trang chi tiết của app (FE tra theo processKey + businessKey)
            notifications.getObject().notify(new Notifications.Notice(workspace, approved ? "ESTIMATE_APPROVED" : "ESTIMATE_REJECTED",
                Notifications.Severity.INFO, (approved ? "Đã duyệt" : "Từ chối") + " sửa ước tính " + request.proposedMinutes() + " phút",
                request.label(), null, task.processKey(), request.businessKey(), actor.subject(), Set.of(request.requestedBy()), false, false));
            return null;
        });
        if (!active) throw CapabilityException.conflict("Estimate request no longer references active work");
        return find(workspace, id);
    }

    private WorkEstimate provider(WorkItem task) {
        return estimates.stream().filter(value -> value.supports(task.processKey())).findFirst()
            .orElseThrow(() -> CapabilityException.conflict("Estimate adjustment not supported for this process"));
    }
    private Request find(String workspace, UUID id) {
        return jdbc.query("select * from hpdi_estimate_request where workspace=? and id=?", this::map, workspace, id).stream()
            .findFirst().orElseThrow(() -> CapabilityException.notFound("Estimate request not found"));
    }
    private Request map(ResultSet row, int index) throws SQLException {
        var decided = row.getTimestamp("decided_at");
        var superseded = row.getTimestamp("superseded_at");
        return new Request(row.getObject("id", UUID.class), row.getString("task_id"), row.getString("process_instance_id"),
            row.getString("business_key"), row.getString("label"), row.getString("requested_by"), row.getInt("current_minutes"),
            row.getInt("proposed_minutes"), row.getString("reason"), Status.valueOf(row.getString("status")),
            row.getTimestamp("created_at").toInstant(), row.getString("decided_by"), decided == null ? null : decided.toInstant(), row.getString("decision_note"),
            row.getString("superseded_by"), superseded == null ? null : superseded.toInstant(), row.getString("superseded_reason"));
    }
}
