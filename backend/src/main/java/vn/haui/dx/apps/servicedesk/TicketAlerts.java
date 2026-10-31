package vn.haui.dx.apps.servicedesk;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import vn.haui.dx.hpdi.human.api.*;
import vn.haui.dx.hpdi.process.api.*;
import static vn.haui.dx.hpdi.human.api.WorkAlerts.Type.*;
import static vn.haui.dx.hpdi.human.api.WorkAlerts.Severity.*;

@Service
public class TicketAlerts {
    private final JdbcTemplate jdbc;
    private final WorkAlerts alerts;
    private final TicketSlaService sla;
    private final WorkScheduling scheduling;
    private final vn.haui.dx.hpdi.settings.api.WorkPolicy work;
    /** Cho test: ngưỡng theo giá trị seed. */
    public TicketAlerts(JdbcTemplate jdbc, WorkAlerts alerts, TicketSlaService sla, WorkScheduling scheduling) {
        this(jdbc, alerts, sla, scheduling, () -> vn.haui.dx.hpdi.settings.api.WorkPolicy.DEFAULTS);
    }
    @org.springframework.beans.factory.annotation.Autowired
    public TicketAlerts(JdbcTemplate jdbc, WorkAlerts alerts, TicketSlaService sla, WorkScheduling scheduling,
                        vn.haui.dx.hpdi.settings.api.WorkPolicy work) {
        this.jdbc = jdbc; this.alerts = alerts; this.sla = sla; this.scheduling = scheduling; this.work = work;
    }

    // Ticket còn ở bước duyệt: chờ duyệt (không qua tiếp nhận) hoặc tiếp nhận đã thả sang duyệt đúng bản đang có
    private static final String AWAITING_REVIEW = "((t.awaiting_review and not exists(select 1 from servicedesk_intake_admission a where a.ticket_id=t.id))"
        + " or exists(select 1 from servicedesk_intake_admission a where a.ticket_id=t.id and a.status='REVIEW_RELEASED' and a.sealed_revision=t.context_revision))";
    @Transactional(propagation = Propagation.MANDATORY)
    public void advisory(Ticket ticket, String generation, String priority) {
        if (!"CRITICAL".equals(priority)) return;
        if (jdbc.queryForList("select id from servicedesk_ticket where workspace=? and id=? for update", ticket.workspace(), ticket.id()).isEmpty()) return;
        boolean awaiting = Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from servicedesk_ticket t where t.workspace=? and t.id=? and"
            + " " + AWAITING_REVIEW + ")", Boolean.class, ticket.workspace(), ticket.id()));
        if (!awaiting) return;
        alerts.publish(new WorkAlerts.Event(ticket.workspace(), CRITICAL_NEW, CRITICAL, ticket.process().processInstanceId(), ticket.process().processKey(),
            ticket.id().toString(), null, "advisory:" + generation, "AI suggests CRITICAL - UNAPPROVED; Coordinator review required", true, null));
        // R7: theo dõi để nhắc lại nếu quá hạn vẫn chưa ai duyệt (không tự duyệt)
        jdbc.update("insert into servicedesk_critical_watch(workspace,ticket_id,process_instance_id,process_key,generation,advised_at)"
            + " values(?,?,?,?,?,?) on conflict(workspace,ticket_id,generation) do nothing", ticket.workspace(), ticket.id(),
            ticket.process().processInstanceId(), ticket.process().processKey(), generation, Timestamp.from(sla.now()));
    }

    /**
     * R7: AI gợi ý CRITICAL mà quá criticalReviewAlertMinutes chưa ai duyệt thì nhắc điều phối viên một lần
     * (CRITICAL_UNREVIEWED). Ticket vẫn chờ người duyệt; đã duyệt thì thôi theo dõi.
     */
    @Transactional
    public void remindUnreviewed(String workspace, UUID ticketId, String generation) {
        var rows = jdbc.queryForList("select w.*,exists(select 1 from servicedesk_ticket t where t.workspace=w.workspace and t.id=w.ticket_id and "
            + AWAITING_REVIEW + ") as awaiting from servicedesk_critical_watch w where w.workspace=? and w.ticket_id=? and w.generation=?"
            + " and w.closed_at is null for update of w", workspace, ticketId, generation);
        if (rows.isEmpty()) return;
        var row = rows.get(0);
        var now = sla.now();
        if (Boolean.TRUE.equals(row.get("awaiting"))) {
            var due = ((Timestamp) row.get("advised_at")).toInstant().plus(Duration.ofMinutes(work.current().criticalReviewAlertMinutes()));
            if (now.isBefore(due)) return;
            alerts.publish(new WorkAlerts.Event(workspace, CRITICAL_UNREVIEWED, CRITICAL, (String) row.get("process_instance_id"),
                (String) row.get("process_key"), ticketId.toString(), null, "unreviewed:" + generation,
                "AI suggests CRITICAL and nobody has reviewed it yet; Coordinator review required", true, null));
        }
        jdbc.update("update servicedesk_critical_watch set closed_at=? where workspace=? and ticket_id=? and generation=?",
            Timestamp.from(now), workspace, ticketId, generation);
    }
    @Transactional(propagation = Propagation.MANDATORY)
    public void reviewed(String workspace, Ticket ticket, String review, String handle, TicketReview.Decision decision) {
        jdbc.update("insert into servicedesk_alert_monitor(workspace,ticket_id,process_instance_id,process_key,review_task_id,handle_task_id,category,assignee,checked_at) values(?,?,?,?,?,?,?,?,?)",
            workspace, ticket.id(), ticket.process().processInstanceId(), ticket.process().processKey(), review, handle, decision.category().name(), decision.assignee(), Timestamp.from(sla.now()));
        if (decision.priority() == TicketReview.Priority.CRITICAL && !Boolean.TRUE.equals(jdbc.queryForObject(
                "select exists(select 1 from servicedesk_work_release where workspace=? and review_task_id=?)", Boolean.class, workspace, review)))
            alerts.publish(new WorkAlerts.Event(workspace, CRITICAL_NEW, CRITICAL, ticket.process().processInstanceId(), ticket.process().processKey(),
                ticket.id().toString(), handle, review, "CRITICAL approved by Coordinator", true, decision.assignee()));
    }
    @Transactional(propagation = Propagation.MANDATORY)
    public void assigned(String workspace, String processId, String subject) {
        jdbc.update("update servicedesk_alert_monitor set assignee=?,assignee_off=false where workspace=? and process_instance_id=? and active", subject, workspace, processId);
    }
    @Transactional(propagation = Propagation.MANDATORY)
    public void execution(String workspace, String task, WorkExecution.Action action, Instant at) {
        if (action == WorkExecution.Action.PAUSE)
            jdbc.update("update servicedesk_alert_monitor set pending_at=coalesce(pending_at,?) where workspace=? and handle_task_id=? and active", Timestamp.from(at), workspace, task);
        else jdbc.update("update servicedesk_alert_monitor set pending_at=null where workspace=? and handle_task_id=? and active", workspace, task);
    }
    @Transactional(propagation = Propagation.MANDATORY)
    public void resolved(String workspace, String task) {
        jdbc.update("update servicedesk_alert_monitor set active=false,pending_at=null where workspace=? and handle_task_id=?", workspace, task);
    }
    @Transactional(propagation = Propagation.MANDATORY)
    public void released(String workspace, String task) {
        resolved(workspace, task);
        alerts.suppressAgent(workspace, task);
    }
    @Transactional
    public void check(String workspace, String processId, String task, Map<String, Boolean> duty) {
        check(workspace, processId, task, duty, duty, null);
    }
    @Transactional
    public void check(String workspace, String processId, String task, Map<String, Boolean> duty,
            Map<String, Boolean> incumbentDuty, String expectedCategory) {
        scheduling.reconcile(workspace, processId, task, () -> {
            var rows = jdbc.queryForList("select * from servicedesk_alert_monitor where workspace=? and handle_task_id=? and active for update", workspace, task);
            if (rows.isEmpty()) return null;
            var row = rows.get(0);
            if (expectedCategory != null && !expectedCategory.equals(row.get("category"))) return null;
            var state = sla.read(workspace, (UUID) row.get("ticket_id"));
            if (state == null || !task.equals(state.taskId()) || state.resolvedAt() != null) return null;
            Instant now = sla.now();
            var projected = state.project(now);
            String review = (String) row.get("review_task_id");
            String agent = (String) row.get("assignee");
            if (state.firstStarted() == null && projected.startBreached()) emit(row, START_OVERDUE, WARNING, review, true, null);
            if (projected.resolveBreached()) emit(row, SLA_BREACHED, CRITICAL, review, true, agent);
            else if (projected.warning() && !projected.clockPaused()) emit(row, SLA_WARNING, WARNING, review, true, agent);
            var pending = (Timestamp) row.get("pending_at");
            if (agent != null && pending != null && now.isAfter(pending.toInstant().plus(Duration.ofDays(work.current().pendingRemindDays()))))
                emit(row, PENDING_TOO_LONG, WARNING, review + ":pending:" + pending.toInstant().toEpochMilli(), false, agent);
            if (duty != null || incumbentDuty != null) {
                boolean empty = duty != null && duty.values().stream().noneMatch(Boolean.TRUE::equals);
                boolean off = incumbentDuty != null && agent != null && !Boolean.TRUE.equals(incumbentDuty.get(agent));
                int groupGeneration = ((Number) row.get("group_generation")).intValue();
                int offGeneration = ((Number) row.get("off_generation")).intValue();
                if (empty && !Boolean.TRUE.equals(row.get("group_empty"))) emit(row, NO_ONE_ON_DUTY, WARNING, review + ":empty:" + ++groupGeneration, true, null);
                if (off && !Boolean.TRUE.equals(row.get("assignee_off"))) emit(row, ASSIGNEE_OFF_DUTY, WARNING, review + ":off:" + ++offGeneration, true, null);
                jdbc.update("update servicedesk_alert_monitor set group_empty=?,group_generation=?,assignee_off=?,off_generation=? where workspace=? and handle_task_id=?",
                    empty, groupGeneration, off, offGeneration, workspace, task);
            }
            jdbc.update("update servicedesk_alert_monitor set checked_at=? where workspace=? and handle_task_id=?", Timestamp.from(now), workspace, task);
            return null;
        });
    }
    private void emit(Map<String, Object> row, WorkAlerts.Type type, WorkAlerts.Severity severity, String generation, boolean coordinators, String agent) {
        alerts.publish(new WorkAlerts.Event((String) row.get("workspace"), type, severity, (String) row.get("process_instance_id"),
            (String) row.get("process_key"), row.get("ticket_id").toString(), (String) row.get("handle_task_id"), generation, type.name(), coordinators, agent));
    }
}
