package vn.haui.dx.apps.servicedesk;

import java.sql.*;
import java.time.*;
import java.util.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import vn.haui.dx.hpdi.human.api.*;
import vn.haui.dx.hpdi.process.api.*;
import vn.haui.dx.hpdi.process.api.SlaPolicy.Priority;

@Service
public class TicketSlaService {
    public record State(UUID ticketId, String processId, String taskId, SlaPolicy policy, Priority base, Priority priority,
                        Instant approvedAt, Instant startBy, Instant resolveBy, Instant firstStarted, Instant resolvedAt, Instant pausedAt,
                        boolean manualPriority, boolean historicalStartBreach, boolean historicalResolveBreach) {
        public State(UUID ticketId, String processId, String taskId, SlaPolicy policy, Priority base, Priority priority,
                Instant approvedAt, Instant startBy, Instant resolveBy, Instant firstStarted, Instant resolvedAt, Instant pausedAt) {
            this(ticketId, processId, taskId, policy, base, priority, approvedAt, startBy, resolveBy, firstStarted, resolvedAt, pausedAt, false, false, false);
        }
        public Sla project(Instant now) {
            var current = policy.project(base, startBy, resolveBy, firstStarted, resolvedAt, pausedAt, now);
            return new Sla(current.startBy(), current.resolveBy(), current.startBreached() || historicalStartBreach,
                current.resolveBreached() || historicalResolveBreach, current.warning(), current.clockPaused());
        }
    }
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    /** Chính sách cho lần duyệt mới; việc đã duyệt dùng bản chụp đã lưu. */
    private final java.util.function.Supplier<SlaPolicy> policies;
    private final Clock clock;
    private final WorkScheduling scheduling;
    private final ActivityJournal audit;

    /** Cho test: một chính sách cố định. */
    public TicketSlaService(JdbcTemplate jdbc, ObjectMapper json, SlaPolicy policy, Clock clock, WorkScheduling scheduling, ActivityJournal audit) {
        this(jdbc, json, () -> policy, clock, scheduling, audit);
    }
    @org.springframework.beans.factory.annotation.Autowired
    public TicketSlaService(JdbcTemplate jdbc, ObjectMapper json, TicketSlaConfiguration configuration, Clock clock, WorkScheduling scheduling, ActivityJournal audit) {
        this(jdbc, json, configuration::current, clock, scheduling, audit);
    }
    private TicketSlaService(JdbcTemplate jdbc, ObjectMapper json, java.util.function.Supplier<SlaPolicy> policies, Clock clock,
                             WorkScheduling scheduling, ActivityJournal audit) {
        this.jdbc = jdbc; this.json = json; this.policies = policies; this.clock = clock; this.scheduling = scheduling; this.audit = audit;
    }
    public Instant now() { return clock.instant(); }

    @Transactional(propagation = Propagation.MANDATORY)
    public void correctPriority(String workspace, UUID ticketId, String taskId, String priority, UUID operation) {
        var state = jdbc.query("select * from servicedesk_sla where workspace=? and handle_task_id=? for update", this::map, workspace, taskId)
            .stream().findFirst().orElseThrow(() -> vn.haui.dx.hpdi.common.api.CapabilityException.conflict("SLA snapshot required"));
        if (state.resolvedAt() != null) throw vn.haui.dx.hpdi.common.api.CapabilityException.conflict("Resolved work cannot be corrected");
        var at = now();
        var next = Priority.valueOf(priority);
        var adjustments = jdbc.queryForMap("select start_budget_adjustment_millis,resolve_budget_adjustment_millis from servicedesk_sla where workspace=? and handle_task_id=?", workspace, taskId);
        var deadlines = ClassificationSlaAmendment.calculate(state.policy(), state.base(), next, state.startBy(),
            state.resolveBy(), state.firstStarted(), state.pausedAt(), at,
            ((Number)adjustments.get("start_budget_adjustment_millis")).longValue(), ((Number)adjustments.get("resolve_budget_adjustment_millis")).longValue());
        var breached = state.project(at);
        jdbc.update("insert into servicedesk_sla_amendment(operation_id,workspace,ticket_id,handle_task_id,before_state)"
            + " select ?,workspace,ticket_id,handle_task_id,to_jsonb(s) from servicedesk_sla s where workspace=? and handle_task_id=?",
            operation, workspace, taskId);
        jdbc.update("update servicedesk_sla set manual_priority=?,priority=?,start_by=?,resolve_by=?,"
            + "start_breached_history=?,resolve_breached_history=?,checked_at=?,start_budget_adjustment_millis=?,resolve_budget_adjustment_millis=? where workspace=? and handle_task_id=?",
            priority, priority, timestamp(deadlines.startBy()), timestamp(deadlines.resolveBy()), breached.startBreached(),
            breached.resolveBreached(), timestamp(at), deadlines.startAdjustmentMillis(), deadlines.resolveAdjustmentMillis(), workspace, taskId);
        jdbc.update("update servicedesk_sla_amendment a set after_state=to_jsonb(s) from servicedesk_sla s"
            + " where a.operation_id=? and s.workspace=a.workspace and s.handle_task_id=a.handle_task_id", operation);
        scheduling.reconcile(workspace, state.processId(), taskId,
            () -> new WorkScheduling.Schedule((next.ordinal()+1)*25, read(workspace, ticketId).project(at).resolveBy()));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void released(String workspace, String taskId) {
        var states = jdbc.query("select * from servicedesk_sla where workspace=? and handle_task_id=? for update", this::map, workspace, taskId);
        if (states.isEmpty()) throw vn.haui.dx.hpdi.common.api.CapabilityException.conflict("SLA snapshot required for handover");
        var state = states.get(0);
        var at = clock.instant();
        var due = state.resolveBy();
        if (state.pausedAt() != null) {
            var target = state.policy().targets().get(state.base());
            due = state.policy().calendar().add(due, state.policy().calendar().between(state.pausedAt(), at, target.continuous()), target.continuous());
        }
        jdbc.update("update servicedesk_sla set released_at=?,paused_at=null,resolve_by=? where workspace=? and handle_task_id=?",
            timestamp(at), timestamp(due), workspace, taskId);
        activity(workspace, state.ticketId(), taskId, "released", false);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void initialize(String workspace, UUID ticketId, String processId, String reviewId, String taskId, String priority) {
        var previous = jdbc.query("select s.* from servicedesk_sla s join servicedesk_work_release h on h.workspace=s.workspace and h.task_id=s.handle_task_id"
            + " where h.workspace=? and h.review_task_id=?", this::map, workspace, reviewId);
        if (!previous.isEmpty()) {
            var state = previous.get(0);
            if (!state.base().name().equals(priority)) throw vn.haui.dx.hpdi.common.api.CapabilityException.conflict("Handover preserves SLA base priority");
            jdbc.update("insert into servicedesk_sla(workspace,ticket_id,process_instance_id,review_task_id,handle_task_id,policy,base_priority,priority,approved_at,start_by,resolve_by,first_started_at,escalated_at,checked_at,manual_priority,start_breached_history,resolve_breached_history,start_budget_adjustment_millis,resolve_budget_adjustment_millis)"
                + " select workspace,ticket_id,process_instance_id,?,?,policy,base_priority,priority,approved_at,start_by,resolve_by,first_started_at,escalated_at,clock_timestamp(),manual_priority,start_breached_history,resolve_breached_history,start_budget_adjustment_millis,resolve_budget_adjustment_millis"
                + " from servicedesk_sla where workspace=? and handle_task_id=?", reviewId, taskId, workspace, state.taskId());
            scheduling.reconcile(workspace, processId, taskId, () -> new WorkScheduling.Schedule((state.priority().ordinal() + 1) * 25, state.resolveBy()));
            activity(workspace, ticketId, taskId, "continued", false);
            return;
        }
        var approved = clock.instant();
        var base = Priority.valueOf(priority);
        var policy = policies.get();
        var target = policy.targets().get(base);
        var startBy = policy.calendar().add(approved, Duration.ofMinutes(target.startMinutes()), target.continuous());
        var resolveBy = policy.calendar().add(approved, Duration.ofMinutes(target.resolveMinutes()), target.continuous());
        jdbc.update("insert into servicedesk_sla(workspace,ticket_id,process_instance_id,review_task_id,handle_task_id,policy,base_priority,priority,approved_at,start_by,resolve_by,checked_at)"
            + " values(?,?,?,?,?,cast(? as jsonb),?,?,?,?,?,?)", workspace, ticketId, processId, reviewId, taskId, write(policy), priority, priority,
            timestamp(approved), timestamp(startBy), timestamp(resolveBy), timestamp(approved));
        scheduling.reconcile(workspace, processId, taskId, () -> new WorkScheduling.Schedule((base.ordinal() + 1) * 25, resolveBy));
        activity(workspace, ticketId, taskId, "initialized", false);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void transition(String workspace, String processId, String taskId, WorkExecution.Action action, String reason, Instant at) {
        scheduling.reconcile(workspace, processId, taskId, () -> {
            var states = jdbc.query("select * from servicedesk_sla where workspace=? and handle_task_id=? for update", this::map, workspace, taskId);
            if (states.isEmpty()) return null;
            var state = states.get(0);
            var first = state.firstStarted();
            var paused = state.pausedAt();
            var due = state.resolveBy();
            var eventAt = at.isBefore(state.approvedAt()) ? state.approvedAt() : at;
            if (action == WorkExecution.Action.START && first == null) first = eventAt;
            if (action == WorkExecution.Action.PAUSE && state.policy().pauses(reason) && paused == null) paused = eventAt;
            if (action == WorkExecution.Action.RESUME && paused != null) {
                var target = state.policy().targets().get(state.base());
                if (eventAt.isBefore(paused)) eventAt = paused;
                due = state.policy().calendar().add(due, state.policy().calendar().between(paused, eventAt, target.continuous()), target.continuous());
                paused = null;
            }
            jdbc.update("update servicedesk_sla set first_started_at=?,paused_at=?,resolve_by=? where workspace=? and handle_task_id=?",
                timestamp(first), timestamp(paused), timestamp(due), workspace, taskId);
            activity(workspace, state.ticketId(), taskId, action.name().toLowerCase(Locale.ROOT), false);
            return new WorkScheduling.Schedule((state.priority().ordinal() + 1) * 25, due);
        });
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void resolved(String workspace, String taskId) {
        var states = jdbc.query("select * from servicedesk_sla where workspace=? and handle_task_id=? for update", this::map, workspace, taskId);
        if (states.isEmpty() || states.get(0).resolvedAt() != null) return;
        var state = states.get(0);
        var at = clock.instant();
        if (state.firstStarted() == null) throw new IllegalStateException("SLA resolution requires START");
        if (at.isBefore(state.firstStarted())) at = state.firstStarted();
        jdbc.update("update servicedesk_sla set resolved_at=?,paused_at=null where workspace=? and handle_task_id=?", timestamp(at), workspace, taskId);
        activity(workspace, state.ticketId(), taskId, "resolved", false);
    }

    @Transactional(readOnly = true)
    public State read(String workspace, UUID ticketId) {
        var rows = jdbc.query("select s.* from servicedesk_sla s join servicedesk_review r on r.ticket_id=s.ticket_id and r.review_task_id=s.review_task_id"
            + " join servicedesk_ticket t on t.id=s.ticket_id where s.workspace=? and s.ticket_id=? and not t.awaiting_review"
            + " and not exists(select 1 from servicedesk_intake_admission a where a.ticket_id=t.id and a.status<>'REVIEWED')", this::map, workspace, ticketId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    @Transactional(readOnly = true)
    public Map<String, State> batch(String workspace, List<String> taskIds) {
        if (taskIds.isEmpty()) return Map.of();
        var parameters = new ArrayList<Object>();
        parameters.add(workspace); parameters.addAll(taskIds);
        var states = jdbc.query("select * from servicedesk_sla where workspace=? and handle_task_id in ("
            + String.join(",", Collections.nCopies(taskIds.size(), "?")) + ")", this::map, parameters.toArray());
        var result = new HashMap<String, State>();
        states.forEach(state -> result.put(state.taskId(), state));
        return result;
    }

    @Transactional
    public void reconcile(String workspace, String processId, String taskId) {
        scheduling.reconcile(workspace, processId, taskId, () -> {
            var rows = jdbc.query("select * from servicedesk_sla where workspace=? and handle_task_id=? for update", this::map, workspace, taskId);
            if (rows.isEmpty() || rows.get(0).resolvedAt() != null) return null;
            var state = rows.get(0);
            var at = clock.instant();
            var effective = state.manualPriority() || state.priority() != state.base() ? state.priority()
                : state.policy().escalate(state.base(), state.approvedAt(), state.firstStarted(), at);
            if (effective != state.priority()) {
                jdbc.update("update servicedesk_sla set priority=?,escalated_at=? where workspace=? and handle_task_id=?",
                    effective.name(), timestamp(at), workspace, taskId);
                activity(workspace, state.ticketId(), taskId, "escalated", true);
            }
            jdbc.update("update servicedesk_sla set checked_at=? where workspace=? and handle_task_id=?", timestamp(at), workspace, taskId);
            return new WorkScheduling.Schedule((effective.ordinal() + 1) * 25, state.project(at).resolveBy());
        });
    }

    private void activity(String workspace, UUID ticketId, String taskId, String action, boolean system) {
        var event = new BusinessActivity("servicedesk.sla." + action, "servicedesk.ticket", ticketId.toString(), Map.of("handleTaskId", taskId));
        if (system) audit.recordSystem(workspace, event); else audit.record(workspace, event);
    }
    private State map(ResultSet row, int index) throws SQLException {
        try {
            return new State(row.getObject("ticket_id", UUID.class), row.getString("process_instance_id"), row.getString("handle_task_id"),
                json.readValue(row.getString("policy"), SlaPolicy.class), Priority.valueOf(row.getString("manual_priority") == null ? row.getString("base_priority") : row.getString("manual_priority")), Priority.valueOf(row.getString("priority")),
                instant(row, "approved_at"), instant(row, "start_by"), instant(row, "resolve_by"), instant(row, "first_started_at"), instant(row, "resolved_at"), instant(row, "paused_at"),
                row.getString("manual_priority") != null, row.getBoolean("start_breached_history"), row.getBoolean("resolve_breached_history"));
        } catch (com.fasterxml.jackson.core.JsonProcessingException failure) { throw new IllegalStateException("Invalid SLA snapshot", failure); }
    }
    private String write(SlaPolicy value) {
        try { return json.writeValueAsString(value); }
        catch (com.fasterxml.jackson.core.JsonProcessingException failure) { throw new IllegalStateException("Cannot snapshot SLA policy", failure); }
    }
    private static Timestamp timestamp(Instant value) { return value == null ? null : Timestamp.from(value); }
    private static Instant instant(ResultSet row, String column) throws SQLException {
        var value = row.getTimestamp(column); return value == null ? null : value.toInstant();
    }
}
