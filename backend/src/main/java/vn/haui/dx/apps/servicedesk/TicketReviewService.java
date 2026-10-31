package vn.haui.dx.apps.servicedesk;

import java.util.*;
import jakarta.validation.Validator;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.haui.dx.hpdi.common.api.*;
import vn.haui.dx.hpdi.human.api.*;
import vn.haui.dx.hpdi.process.api.*;

@Service
public class TicketReviewService implements WorkProjection {
    private final JdbcTemplate jdbc;
    private final TicketRepository tickets;
    private final HumanContext humans;
    private final PermissionGuard permissions;
    private final WorkInbox inbox;
    private final WorkExecution execution;
    private final ProcessCapability processes;
    private final ActivityJournal audit;
    private final Validator validator;
    private final TicketSlaService sla;
    private final TicketAlerts alerts;
    private final TicketConfirmation confirmation;
    private final vn.haui.dx.apps.servicedesk.intelligence.BackgroundTicketAssessment assessments;
    private final vn.haui.dx.apps.servicedesk.intake.IntakeAdmission intake;
    private final TicketEstimates estimates;
    private final TicketAssignmentEligibility eligibility;
    private final vn.haui.dx.hpdi.settings.api.WorkPolicy work;
    private final TicketLabels labels;

    public TicketReviewService(JdbcTemplate jdbc, TicketRepository tickets, HumanContext humans, PermissionGuard permissions,
                              WorkInbox inbox, WorkExecution execution, ProcessCapability processes, ActivityJournal audit, Validator validator,
                              vn.haui.dx.apps.servicedesk.intelligence.BackgroundTicketAssessment assessments,
                              vn.haui.dx.apps.servicedesk.intake.IntakeAdmission intake, TicketSlaService sla, TicketAlerts alerts,
                              TicketConfirmation confirmation, TicketEstimates estimates, TicketAssignmentEligibility eligibility,
                              vn.haui.dx.hpdi.settings.api.WorkPolicy work, TicketLabels labels) {
        this.jdbc=jdbc; this.tickets=tickets; this.humans=humans; this.permissions=permissions;
        this.inbox=inbox; this.execution=execution; this.processes=processes; this.audit=audit; this.validator=validator;
        this.assessments=assessments;
        this.intake=intake;
        this.sla=sla;
        this.alerts=alerts;
        this.confirmation=confirmation;
        this.estimates=estimates;
        this.eligibility=eligibility;
        this.work=work;
        this.labels=labels;
    }
    @Override public String processKey() { return ServiceDeskProcess.REVIEWED_KEY; }

    private TicketRealtime realtime;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    void setRealtime(TicketRealtime realtime) { this.realtime = realtime; }

    @Transactional
    public TicketReview.View review(String workspace, UUID id, TicketReview.Decision decision) {
        var actor = humans.currentActor(workspace);
        if (!actor.hasRole("coordinator")) throw CapabilityException.forbidden("Coordinator required");
        permissions.require(workspace, Permissions.TASK_ASSIGN);
        permissions.require(workspace, Permissions.TASK_HANDLE);
        if (decision == null || !validator.validate(decision).isEmpty()) throw CapabilityException.validation("Invalid review decision");
        // Phát sau commit; duyệt lỗi thì rollback, không phát
        if (realtime != null) realtime.changed(workspace, id);
        var locked = jdbc.queryForList("select id from servicedesk_ticket where workspace=? and id=? for update", workspace, id);
        if (locked.isEmpty()) throw CapabilityException.notFound("Ticket not found");
        var ticket = tickets.find(workspace, id, null).orElseThrow();
        if (!ServiceDeskProcess.reviewed(ticket.process().processKey()))
            throw CapabilityException.conflict("Legacy ticket keeps its existing workflow");
        if (actor.subject().equals(ticket.creator())) throw CapabilityException.forbidden("Independent reviewer required");
        if (ticket.creator().equals(decision.assignee())) throw CapabilityException.forbidden("Independent agent required");
        var timeline = processes.timeline(workspace, ticket.process().processInstanceId());
        var review = timeline.currentTasks().stream().filter(task -> "review".equals(task.key())).findFirst()
            .orElseThrow(() -> CapabilityException.conflict("Ticket is not awaiting review"));
        intake.approveReview(workspace, id, decision.expectedContextRevision(), decision.expectedIntakeVersion(), review.id());
        // ET mặc định: trung vị việc tương tự hoặc bảng cấu hình (R2); bàn giao giữ ET điều phối viên đã duyệt
        var computed = estimates.estimate(workspace, decision.category(), decision.priority());
        int defaults = computed.minutes();
        String initialBasis = computed.basis();
        Integer initialSamples = computed.sampleSize();
        var handover = jdbc.queryForList("select r.estimate_minutes,r.estimate_basis from servicedesk_work_release h join servicedesk_review r on r.ticket_id=h.ticket_id"
            + " where h.workspace=? and h.review_task_id=?", workspace, review.id());
        if (!handover.isEmpty() && "COORDINATOR".equals(handover.get(0).get("estimate_basis"))) {
            defaults = ((Number) handover.get(0).get("estimate_minutes")).intValue();
            initialBasis = "COORDINATOR";
            initialSamples = null;
        }
        int minutes = decision.estimateMinutes() == null ? defaults : decision.estimateMinutes();
        String basis = decision.estimateMinutes() == null ? initialBasis : "COORDINATOR";
        Integer samples = decision.estimateMinutes() == null ? initialSamples : null;
        jdbc.update("update servicedesk_ticket set awaiting_review=false where id=?",id);
        jdbc.update("delete from servicedesk_review where ticket_id=?",id);
        jdbc.update("insert into servicedesk_review(ticket_id,category,priority,estimate_minutes,estimate_basis,estimate_samples,assignee,review_task_id,reviewed_by,reason,impact,urgency)"
            + " values(?,?,?,?,?,?,?,?,?,?,?,?)",
            id, decision.category().name(), decision.priority().name(), minutes, basis, samples, decision.assignee(), review.id(), actor.subject(), decision.reason(), decision.impact(), decision.urgency());
        intake.feedback(workspace, id, review.id(), decision, actor.subject(), decision.reason());
        jdbc.update("insert into servicedesk_estimate_history(ticket_id,review_task_id,category,priority,assignee,minutes,basis,changed_by,reason) values(?,?,?,?,?,?,?,?,?)",
            id, review.id(), decision.category().name(), decision.priority().name(), decision.assignee(), defaults, initialBasis, actor.subject(), decision.reason());
        if (decision.estimateMinutes() != null)
            jdbc.update("insert into servicedesk_estimate_history(ticket_id,review_task_id,category,priority,assignee,minutes,basis,changed_by,reason) values(?,?,?,?,?,?,?,?,?)",
                id, review.id(), decision.category().name(), decision.priority().name(), decision.assignee(), minutes, basis, actor.subject(), decision.reason());
        if (review.assignee() == null) inbox.claim(workspace, review.id());
        inbox.complete(workspace, review.id(), "review", "approved", decision.reason());
        var handle = processes.timeline(workspace, ticket.process().processInstanceId()).currentTasks().stream()
            .filter(task -> "handle".equals(task.key())).findFirst().orElseThrow();
        inbox.setPriority(workspace, handle.id(), (decision.priority().ordinal() + 1) * 25);
        sla.initialize(workspace, id, ticket.process().processInstanceId(), review.id(), handle.id(), decision.priority().name());
        if (decision.assignee() != null) inbox.assign(workspace, handle.id(), decision.assignee());
        alerts.reviewed(workspace, ticket, review.id(), handle.id(), decision);
        audit.record(workspace, new BusinessActivity("servicedesk.ticket.review", "servicedesk.ticket", id.toString(),
            Map.of("processInstanceId",ticket.process().processInstanceId(),"reviewTaskId",review.id())));
        return view(workspace, ticket);
    }

    @Transactional(readOnly=true)
    public TicketReview.View view(String workspace, Ticket ticket) {
        var timeline = processes.timeline(workspace, ticket.process().processInstanceId());
        var task = timeline.currentTasks().stream().findFirst().orElse(null);
        int reopened = (int) timeline.steps().stream().filter(step -> "confirm".equals(step.key()) && "rework".equals(step.outcome())).count();
        boolean closedFollower = timeline.ended() && Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from servicedesk_canonical_link "
                + "where workspace=? and follower_id=? and state='CLOSED')", Boolean.class, workspace, ticket.id()));
        boolean ownerClosed=timeline.ended() && Boolean.TRUE.equals(jdbc.queryForObject(
            "select closed_at is not null from servicedesk_ticket where id=?",Boolean.class,ticket.id()));
        String state = timeline.cancelled() ? "CANCELLED" : timeline.ended() ? (ownerClosed || closedFollower || timeline.steps().stream().anyMatch(step -> "confirm".equals(step.key())
            && "accepted".equals(step.outcome())) ? "CLOSED" : "UNCLASSIFIED") : task == null ? "UNCLASSIFIED" : switch (task.key()) {
            case "intake" -> "INTAKE"; case "follower" -> "FOLLOWING";
            case "review" -> reopened > 0 ? "REOPENED" : "AWAITING_REVIEW"; case "confirm" -> "AWAITING_CONFIRMATION"; default -> "ASSIGNED";
        };
        QueueItem item = null;
        if (task != null && "handle".equals(task.key()) && ServiceDeskProcess.reviewed(task.processKey()))
            item = project(workspace, execution.get(workspace, task.id()));
        var estimates = jdbc.query("select estimate_minutes,estimate_basis,estimate_samples from servicedesk_review where ticket_id=?",
            (row,index) -> estimate(row.getInt(1),row.getString(2),(Integer) row.getObject(3)), ticket.id());
        if (item != null) state = switch(item.workState()) { case "IN_PROGRESS" -> "IN_PROGRESS"; case "PENDING" -> "PENDING"; default -> reopened > 0 ? "REOPENED" : "ASSIGNED"; };
        var classifications=jdbc.query("select * from servicedesk_review where ticket_id=?",(row,index) ->
            new TicketReview.Classification(row.getString("category"),row.getString("priority"),row.getString("priority"),false,
                Objects.requireNonNullElse(eligibility.groupOf(TicketReview.Category.valueOf(row.getString("category"))),"manual"),"COORDINATOR",row.getString("impact"),row.getString("urgency"),row.getString("reviewed_by"),
                row.getTimestamp("reviewed_at").toInstant(),false),ticket.id());
        long closedWork=jdbc.queryForObject("select worked_millis from servicedesk_ticket where id=?",Long.class,ticket.id());
        var slaState = sla.read(workspace, ticket.id());
        var classification = classifications.isEmpty() ? null : classifications.get(0);
        if (classification != null && slaState != null)
            classification = new TicketReview.Classification(classification.category(), slaState.priority().name(), slaState.base().name(),
                slaState.priority() != slaState.base(), classification.group(), classification.source(), classification.impact(), classification.urgency(),
                classification.reviewedBy(), classification.reviewedAt(), false);
        var estimate = estimates.isEmpty() ? null : estimates.get(0);
        // Chưa duyệt: ticket mang nhãn AI gán khi chạy nền (loại, ưu tiên, ET)
        if (classification == null) {
            var label = labels.read(ticket.id());
            classification = aiClassification(label);
            if (label != null && label.estimate() != null)
                estimate = estimate(label.estimate().minutes(), label.estimate().basis(), label.estimate().sampleSize());
        }
        return new TicketReview.View(ticket,state,task==null?null:task.id(),task==null?null:task.assignee(),
            estimate,item==null?null:item.workState(),item==null?closedWork/60_000:item.workedMinutes(),
            item==null?null:item.remainingMinutes(),reopened,classification,assessments.read(workspace,ticket.id()),
            slaState == null ? null : slaState.project(sla.now()), correctionHistory(workspace, ticket), confirmation.read(workspace, ticket.id()));
    }

    private TicketReview.Classification aiClassification(TicketLabels.Label label) {
        if (label == null) return null;
        String group = label.category() == null ? "manual"
            : Objects.requireNonNullElse(eligibility.groupOf(TicketReview.Category.valueOf(label.category())), "manual");
        return new TicketReview.Classification(label.category(), label.priority(), label.priority(), false, group, "AI",
            label.impact(), label.urgency(), null, label.labeledAt(), label.priorityDefaulted());
    }

    private List<TicketCorrection.History> correctionHistory(String workspace, Ticket ticket) {
        var actor = humans.currentActor(workspace);
        if (!actor.hasRole("agent", "coordinator") || actor.subject().equals(ticket.creator())) return null;
        if (!Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from servicedesk_classification_correction where workspace=? and ticket_id=?)",
                Boolean.class, workspace, ticket.id()))) return List.of();
        var current = humans.currentMember(workspace);
        if (!current.hasRole("agent", "coordinator")) return null;
        permissions.requireCurrent(workspace, current.hasRole("coordinator") ? "coordinator" : "agent", Permissions.TASK_HANDLE);
        return jdbc.query("select * from servicedesk_classification_correction where workspace=? and ticket_id=? order by changed_at,operation_id,field",
            (row,index) -> new TicketCorrection.History(row.getObject("operation_id", UUID.class), row.getString("process_instance_id"),
                row.getString("review_task_id"), row.getString("handle_task_id"), row.getString("field"), row.getString("previous_value"),
                row.getString("new_value"), row.getBoolean("from_ai"), row.getString("changed_by"), row.getString("reason"),
                row.getTimestamp("changed_at").toInstant()), workspace, ticket.id());
    }

    @Override public QueueItem project(String workspace, WorkExecution.Item execution) {
        return projectBatch(workspace, List.of(execution)).get(0);
    }

    @Override public List<QueueItem> projectBatch(String workspace, List<WorkExecution.Item> executions) {
        if (executions.isEmpty()) return List.of();
        var parameters = new ArrayList<Object>();
        parameters.add(workspace);
        parameters.add(ServiceDeskProcess.REVIEWED_KEY);
        parameters.add(ServiceDeskProcess.INTAKE_KEY);
        executions.forEach(item -> parameters.add(item.task().processInstanceId()));
        var rows = jdbc.queryForList("select t.process_instance_id,t.title,t.worked_millis,r.priority,r.estimate_minutes,r.estimate_basis,r.estimate_samples,"
            + " (select greatest(count(distinct h.review_task_id)-1-(select count(*) from servicedesk_work_release x where x.ticket_id=t.id),0) from servicedesk_estimate_history h where h.ticket_id=t.id) as reopened"
            + " from servicedesk_ticket t join servicedesk_review r on r.ticket_id=t.id where t.workspace=? and t.process_key in (?,?)"
            + " and t.process_instance_id in (" + String.join(",", Collections.nCopies(executions.size(), "?")) + ")", parameters.toArray());
        var byProcess = new HashMap<String,Map<String,Object>>();
        rows.forEach(row -> byProcess.put((String)row.get("process_instance_id"),row));
        var slaStates = sla.batch(workspace, executions.stream().map(item -> item.task().id()).toList());
        var now = sla.now();
        var settings = work.current();
        return executions.stream().map(item -> {
            var row = byProcess.get(item.task().processInstanceId());
            if (row == null) throw CapabilityException.conflict("Reviewed work projection required");
            var projected = projected(item, row, settings);
            var state = slaStates.get(item.task().id());
            return state == null ? projected : new QueueItem(projected.task(), projected.label(), projected.workState(),
                state.priority().name(), state.base().name(), projected.estimate(), projected.workedMinutes(), projected.remainingMinutes(),
                projected.reopenCount(), projected.pendingReason(), projected.pendingNote(), state.project(now));
        }).toList();
    }

    private QueueItem projected(WorkExecution.Item execution, Map<String,Object> row, vn.haui.dx.hpdi.settings.api.WorkPolicy.Snapshot settings) {
        int minutes=((Number)row.get("estimate_minutes")).intValue();
        long worked=(execution.workedMillis()+((Number)row.get("worked_millis")).longValue())/60_000;
        long remaining=remainingMinutes(execution.workState(), minutes, worked, settings.minRemainingMinutes(), settings.pendingLoadFactor());
        int reopened=((Number)row.get("reopened")).intValue();
        return new QueueItem(execution.task(),(String)row.get("title"),execution.workState(),(String)row.get("priority"),
            (String)row.get("priority"),estimate(minutes,(String)row.get("estimate_basis"),(Integer)row.get("estimate_samples")),worked,remaining,reopened,
            execution.pendingReason(),execution.pendingNote());
    }

    /**
     * Phần tính vào tải (R2): chưa đụng tới thì bằng ET; đã làm thì ET trừ phần đã làm, không dưới mức tối thiểu;
     * Đang chờ thì nhân hệ số (làm tròn lên). Mặc định 10 phút và 0,5 như công thức cũ.
     */
    static long remainingMinutes(String workState, int estimate, long worked, int minimum, double pendingFactor) {
        long remaining = "NOT_STARTED".equals(workState) && worked == 0 ? estimate : Math.max(minimum, estimate - worked);
        // Trừ sai số dấu phẩy động trước khi làm tròn lên (10 × 0,3 = 3,0000000000000004)
        return "PENDING".equals(workState) ? (long) Math.ceil(remaining * pendingFactor - 1e-9) : remaining;
    }

    private static QueueItem.Estimate estimate(int minutes, String basis, Integer samples) {
        return new QueueItem.Estimate(minutes, basis, "SIMILAR".equals(basis) ? samples : null);
    }
}
