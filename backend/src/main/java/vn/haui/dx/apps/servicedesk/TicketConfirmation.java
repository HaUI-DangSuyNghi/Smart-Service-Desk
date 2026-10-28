package vn.haui.dx.apps.servicedesk;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import vn.haui.dx.hpdi.common.api.CapabilityException;
import vn.haui.dx.hpdi.human.api.*;
import vn.haui.dx.hpdi.process.api.*;

@Service
public class TicketConfirmation {
    private final JdbcTemplate jdbc;
    private final ActivityJournal audit;
    private final HumanContext human;
    private final WorkAlerts alerts;
    private final ProcessCapability processes;
    private final Clock clock;
    private final vn.haui.dx.hpdi.settings.api.WorkPolicy work;

    public TicketConfirmation(JdbcTemplate jdbc, ActivityJournal audit, HumanContext human, WorkAlerts alerts,
            ProcessCapability processes, Clock clock, vn.haui.dx.hpdi.settings.api.WorkPolicy work) {
        this.jdbc = jdbc; this.audit = audit; this.human = human; this.alerts = alerts;
        this.processes = processes; this.clock = clock; this.work = work;
    }

    // Hạn lưu vào servicedesk_confirmation lúc tạo nên đổi cấu hình chỉ áp dụng cho lần xác nhận sau
    /** R11: tự đóng sau bấy nhiêu ngày liên tục (không tính theo ngày làm việc). */
    private Duration closeWait() { return Duration.ofDays(work.current().autoCloseAfterDays()); }
    /** R11: người dùng báo chưa xong mà quá bấy nhiêu phút chưa ai nhận lại thì báo. */
    private Duration reopenWait() { return Duration.ofMinutes(work.current().reopenUnclaimedMinutes()); }
    /** Nhãn chính sách ghi vào audit, ví dụ r11-72h-v1 với mặc định 3 ngày. */
    private String closePolicy() { return "r11-" + closeWait().toHours() + "h-v1"; }

    @Transactional(propagation = Propagation.MANDATORY)
    public void completed(String workspace, String key, String processId, WorkItem completed, String outcome, List<WorkItem> active) {
        if (!ServiceDeskProcess.reviewed(key)) return;
        var ticket = jdbc.queryForMap("select id,creator from servicedesk_ticket where workspace=? and process_instance_id=?", workspace, processId);
        UUID id = (UUID) ticket.get("id");
        if ("handle".equals(completed.key()) && "resolved".equals(outcome)) {
            var confirm = active.stream().filter(task -> "confirm".equals(task.key())).findFirst()
                .orElseThrow(() -> CapabilityException.conflict("Expected confirmation task"));
            create(workspace, processId, id, confirm);
        } else if ("confirm".equals(completed.key())) {
            create(workspace, processId, id, completed);
            String actor = human.currentActor(workspace).subject();
            if (!actor.equals(ticket.get("creator")) || !actor.equals(completed.assignee()))
                throw CapabilityException.forbidden("Requester confirmation required");
            Instant now = clock.instant();
            String decision = "accepted".equals(outcome) ? "REQUESTER" : "REWORK";
            int changed = jdbc.update("update servicedesk_confirmation set decision=?,decided_at=?,decided_by=?,reopen_deadline_at=?"
                + " where workspace=? and process_instance_id=? and generation=? and decision is null", decision, Timestamp.from(now), actor,
                "REWORK".equals(decision) ? Timestamp.from(now.plus(reopenWait())) : null, workspace, processId, completed.id());
            if (changed != 1) throw CapabilityException.conflict("Confirmation generation already decided");
            audit.record(workspace, new BusinessActivity("servicedesk.confirmation.decided", "servicedesk.ticket", id.toString(),
                Map.of("generationId", completed.id(), "decisionKey", decision, "processInstanceId", processId)));
            if ("REWORK".equals(decision)) emit(workspace, key, processId, id.toString(), completed.id(),
                WorkAlerts.Type.REOPENED, "Requester requested rework; Coordinator admission/review required");
        } else if ("intake".equals(completed.key()) && "duplicate".equals(outcome)) {
            received(workspace, processId, "CANONICAL");
        }
    }

    private void create(String workspace, String processId, UUID id, WorkItem confirm) {
        if (Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from servicedesk_confirmation where workspace=? and generation=?)",
                Boolean.class, workspace, confirm.id()))) return;
        var handle = processes.timeline(workspace, processId).steps().stream()
            .filter(step -> "handle".equals(step.key()) && "resolved".equals(step.outcome()) && step.endedAt() != null
                && !step.endedAt().isAfter(confirm.createdAt()))
            .max(java.util.Comparator.comparing(ProcessTimeline.Step::endedAt))
            .orElseThrow(() -> CapabilityException.conflict("Resolved handle history required"));
        jdbc.update("insert into servicedesk_confirmation(workspace,ticket_id,process_instance_id,generation,handle_task_id,handle_resolved_at,confirm_started_at,deadline_at)"
            + " values(?,?,?,?,?,?,?,?)", workspace, id, processId, confirm.id(), handle.id(), Timestamp.from(handle.endedAt()),
            Timestamp.from(confirm.createdAt()), Timestamp.from(confirm.createdAt().plus(closeWait())));
        audit.record(workspace, new BusinessActivity("servicedesk.confirmation.started", "servicedesk.ticket", id.toString(),
            Map.of("generationId", confirm.id(), "policyVersion", closePolicy())));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void received(String workspace, String processId, String reason) {
        jdbc.update("update servicedesk_confirmation set reopen_received_at=?,reopen_received_reason=?"
            + " where workspace=? and process_instance_id=? and decision='REWORK' and reopen_received_at is null",
            Timestamp.from(clock.instant()), reason, workspace, processId);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void enroll(String workspace, String key, String processId, String businessKey,
            String owner, String generation, Instant createdAt, String handleId, Instant resolvedAt, Instant now) {
        if (!ServiceDeskProcess.reviewed(key)) return;
        if (Boolean.TRUE.equals(jdbc.queryForObject(
                "select exists(select 1 from servicedesk_confirmation where workspace=? and generation=?)",
                Boolean.class, workspace, generation))) return;
        var tickets = jdbc.queryForList("select id,creator from servicedesk_ticket where workspace=? and id=?::uuid and process_instance_id=? and process_key=?",
            workspace, businessKey, processId, key);
        if (tickets.isEmpty() || !owner.equals(tickets.get(0).get("creator"))) return;
        if (resolvedAt == null || resolvedAt.isAfter(createdAt) || createdAt.isAfter(now)) return;
        if (!Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from servicedesk_sla where workspace=?"
                + " and ticket_id=?::uuid and process_instance_id=? and handle_task_id=? and resolved_at is not null)",
                Boolean.class, workspace, businessKey, processId, handleId))) return;
        Instant deadline = createdAt.plus(closeWait());
        boolean hold = !now.isBefore(deadline);
        int inserted = jdbc.update("insert into servicedesk_confirmation(workspace,ticket_id,process_instance_id,generation,handle_task_id,handle_resolved_at,confirm_started_at,deadline_at,rollout_hold,rollout_reason)"
            + " values(?,?::uuid,?,?,?,?,?,?,?,?) on conflict(workspace,process_instance_id,generation) do nothing",
            workspace, businessKey, processId, generation, handleId, Timestamp.from(resolvedAt),
            Timestamp.from(createdAt), Timestamp.from(deadline), hold, hold ? "OVERDUE_AT_ROLLOUT" : null);
        if (inserted > 0) {
            audit.recordSystem(workspace, new BusinessActivity("servicedesk.confirmation.enrolled", "servicedesk.ticket", businessKey,
                Map.of("generationId", generation, "rolloutHoldKey", Boolean.toString(hold), "policyVersion", closePolicy())));
        }
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public String evaluate(String workspace, String key, String processId, String businessKey, String owner,
            String generation, WorkItem active, Instant now) {
        if (!ServiceDeskProcess.reviewed(key)) return null;
        var rows = jdbc.queryForList("select c.*,t.creator,t.process_key from servicedesk_confirmation c join servicedesk_ticket t"
            + " on t.workspace=c.workspace and t.id=c.ticket_id where c.workspace=? and c.process_instance_id=?"
            + " and c.generation=? and t.process_instance_id=c.process_instance_id for update of c", workspace, processId, generation);
        if (rows.isEmpty()) return null;
        var row = rows.get(0);
        if (!businessKey.equals(row.get("ticket_id").toString()) || !owner.equals(row.get("creator")) || !key.equals(row.get("process_key"))) return null;
        jdbc.update("update servicedesk_confirmation set checked_at=? where workspace=? and process_instance_id=? and generation=?",
            Timestamp.from(now), workspace, processId, generation);
        if (row.get("decision") == null) {
            if (Boolean.TRUE.equals(row.get("rollout_hold"))) return null;
            if (!"confirm".equals(active.key()) || !generation.equals(active.id()) || !owner.equals(active.assignee())
                    || !((Timestamp) row.get("confirm_started_at")).toInstant().equals(active.createdAt())
                    || now.isBefore(((Timestamp) row.get("deadline_at")).toInstant())) return null;
            if (!Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from servicedesk_sla where workspace=?"
                    + " and ticket_id=? and process_instance_id=? and handle_task_id=? and resolved_at is not null)",
                    Boolean.class, workspace, row.get("ticket_id"), processId, row.get("handle_task_id")))) return null;
            jdbc.update("update servicedesk_confirmation set decision='AUTO',decided_at=?,decided_by='system:workflow'"
                + " where workspace=? and process_instance_id=? and generation=?", Timestamp.from(now), workspace, processId, generation);
            audit.recordSystem(workspace, new BusinessActivity("servicedesk.confirmation.autoclosed", "servicedesk.ticket", businessKey,
                Map.of("generationId", generation, "processInstanceId", processId, "policyVersion", closePolicy())));
            return "accepted";
        }
        if ("REWORK".equals(row.get("decision")) && row.get("reopen_received_at") == null
                && row.get("reopen_alerted_at") == null
                && !now.isBefore(((Timestamp) row.get("reopen_deadline_at")).toInstant())
                && java.util.Set.of("intake", "review", "handle").contains(active.key())) {
            jdbc.update("update servicedesk_confirmation set reopen_alerted_at=? where workspace=? and process_instance_id=? and generation=?",
                Timestamp.from(now), workspace, processId, generation);
            emit(workspace, key, processId, businessKey, generation, WorkAlerts.Type.REOPEN_UNCLAIMED,
                "Reopened ticket awaiting " + ("handle".equals(active.key()) ? "authorized Agent assignment" : "Coordinator " + active.key()));
        }
        return null;
    }

    public record State(Instant closedAt, String closedBy, Instant handleResolvedAt, Instant confirmStartedAt,
                        Instant confirmDeadlineAt, String confirmGeneration, List<History> confirmationHistory) {}
    public record History(String generation, Instant confirmStartedAt, Instant deadlineAt, String decision,
                          Instant decidedAt, String decidedBy, Instant reopenDeadlineAt, Instant reopenReceivedAt,
                          String reopenReceivedReason, Instant reopenAlertedAt, Boolean rolloutHold) {}

    public State read(String workspace, UUID id) {
        var history = jdbc.query("select * from servicedesk_confirmation where workspace=? and ticket_id=? order by confirm_started_at,generation",
            (row, index) -> new History(row.getString("generation"), row.getTimestamp("confirm_started_at").toInstant(),
                row.getTimestamp("deadline_at").toInstant(), row.getString("decision"), instant(row.getTimestamp("decided_at")),
                row.getString("decided_by"), instant(row.getTimestamp("reopen_deadline_at")), instant(row.getTimestamp("reopen_received_at")),
                row.getString("reopen_received_reason"), instant(row.getTimestamp("reopen_alerted_at")),
                row.getBoolean("rollout_hold") ? Boolean.TRUE : null), workspace, id);
        if (history.isEmpty()) return null;
        var latest = history.get(history.size() - 1);
        Instant resolved = jdbc.queryForObject("select handle_resolved_at from servicedesk_confirmation where workspace=? and generation=?",
            Timestamp.class, workspace, latest.generation()).toInstant();
        boolean closed = java.util.Set.of("REQUESTER", "AUTO").contains(latest.decision() == null ? "" : latest.decision());
        return new State(closed ? latest.decidedAt() : null, closed ? latest.decision() : null, resolved,
            latest.confirmStartedAt(), latest.deadlineAt(), latest.generation(), history);
    }

    private static Instant instant(Timestamp value) { return value == null ? null : value.toInstant(); }

    private void emit(String workspace, String key, String processId, String ticketId, String generation,
            WorkAlerts.Type type, String label) {
        alerts.publish(new WorkAlerts.Event(workspace, type, WorkAlerts.Severity.WARNING, processId, key, ticketId,
            generation, generation, label, true, null));
    }
}
