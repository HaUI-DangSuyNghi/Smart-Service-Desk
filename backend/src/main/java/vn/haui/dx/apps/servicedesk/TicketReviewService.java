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

    public TicketReviewService(JdbcTemplate jdbc, TicketRepository tickets, HumanContext humans, PermissionGuard permissions,
                              WorkInbox inbox, WorkExecution execution, ProcessCapability processes, ActivityJournal audit, Validator validator) {
        this.jdbc=jdbc; this.tickets=tickets; this.humans=humans; this.permissions=permissions;
        this.inbox=inbox; this.execution=execution; this.processes=processes; this.audit=audit; this.validator=validator;
    }
    @Override public String processKey() { return ServiceDeskProcess.REVIEWED_KEY; }

    @Transactional
    public TicketReview.View review(String workspace, UUID id, TicketReview.Decision decision) {
        var actor = humans.currentActor(workspace);
        if (!actor.hasRole("coordinator")) throw CapabilityException.forbidden("Coordinator required");
        permissions.require(workspace, Permissions.TASK_ASSIGN);
        permissions.require(workspace, Permissions.TASK_HANDLE);
        if (decision == null || !validator.validate(decision).isEmpty()) throw CapabilityException.validation("Invalid review decision");
        var locked = jdbc.queryForList("select id from servicedesk_ticket where workspace=? and id=? for update", workspace, id);
        if (locked.isEmpty()) throw CapabilityException.notFound("Ticket not found");
        var ticket = tickets.find(workspace, id, null).orElseThrow();
        if (!ServiceDeskProcess.REVIEWED_KEY.equals(ticket.process().processKey()))
            throw CapabilityException.conflict("Legacy ticket keeps its existing workflow");
        if (actor.subject().equals(ticket.creator())) throw CapabilityException.forbidden("Independent reviewer required");
        if (ticket.creator().equals(decision.assignee())) throw CapabilityException.forbidden("Independent agent required");
        var timeline = processes.timeline(workspace, ticket.process().processInstanceId());
        var review = timeline.currentTasks().stream().filter(task -> "review".equals(task.key())).findFirst()
            .orElseThrow(() -> CapabilityException.conflict("Ticket is not awaiting review"));
        int defaults = TicketReview.defaultMinutes(decision.category(), decision.priority());
        int minutes = decision.estimateMinutes() == null ? defaults : decision.estimateMinutes();
        String basis = decision.estimateMinutes() == null ? "DEFAULT" : "COORDINATOR";
        jdbc.update("update servicedesk_ticket set awaiting_review=false where id=?",id);
        jdbc.update("delete from servicedesk_review where ticket_id=?",id);
        jdbc.update("insert into servicedesk_review(ticket_id,category,priority,estimate_minutes,estimate_basis,assignee,review_task_id,reviewed_by,reason,impact,urgency)"
            + " values(?,?,?,?,?,?,?,?,?,?,?)",
            id, decision.category().name(), decision.priority().name(), minutes, basis, decision.assignee(), review.id(), actor.subject(), decision.reason(), decision.impact(), decision.urgency());
        jdbc.update("insert into servicedesk_estimate_history(ticket_id,review_task_id,category,priority,assignee,minutes,basis,changed_by,reason) values(?,?,?,?,?,?,?,?,?)",
            id, review.id(), decision.category().name(), decision.priority().name(), decision.assignee(), defaults, "DEFAULT", actor.subject(), decision.reason());
        if (decision.estimateMinutes() != null)
            jdbc.update("insert into servicedesk_estimate_history(ticket_id,review_task_id,category,priority,assignee,minutes,basis,changed_by,reason) values(?,?,?,?,?,?,?,?,?)",
                id, review.id(), decision.category().name(), decision.priority().name(), decision.assignee(), minutes, basis, actor.subject(), decision.reason());
        if (review.assignee() == null) inbox.claim(workspace, review.id());
        inbox.complete(workspace, review.id(), "review", "approved", decision.reason());
        var handle = processes.timeline(workspace, ticket.process().processInstanceId()).currentTasks().stream()
            .filter(task -> "handle".equals(task.key())).findFirst().orElseThrow();
        inbox.setPriority(workspace, handle.id(), (decision.priority().ordinal() + 1) * 25);
        if (decision.assignee() != null) inbox.assign(workspace, handle.id(), decision.assignee());
        audit.record(workspace, new BusinessActivity("servicedesk.ticket.review", "servicedesk.ticket", id.toString(),
            Map.of("processInstanceId",ticket.process().processInstanceId(),"reviewTaskId",review.id())));
        return view(workspace, ticket);
    }

    @Transactional(readOnly=true)
    public TicketReview.View view(String workspace, Ticket ticket) {
        var timeline = processes.timeline(workspace, ticket.process().processInstanceId());
        var task = timeline.currentTasks().stream().findFirst().orElse(null);
        int reopened = (int) timeline.steps().stream().filter(step -> "confirm".equals(step.key()) && "rework".equals(step.outcome())).count();
        String state = timeline.ended() ? (timeline.steps().stream().anyMatch(step -> "confirm".equals(step.key())
            && "accepted".equals(step.outcome())) ? "CLOSED" : "UNCLASSIFIED") : task == null ? "UNCLASSIFIED" : switch (task.key()) {
            case "review" -> reopened > 0 ? "REOPENED" : "AWAITING_REVIEW"; case "confirm" -> "AWAITING_CONFIRMATION"; default -> "ASSIGNED";
        };
        QueueItem item = null;
        if (task != null && "handle".equals(task.key()) && ServiceDeskProcess.REVIEWED_KEY.equals(task.processKey()))
            item = project(workspace, execution.get(workspace, task.id()));
        var estimates = jdbc.query("select estimate_minutes,estimate_basis from servicedesk_review where ticket_id=?",
            (row,index) -> new QueueItem.Estimate(row.getInt(1),row.getString(2)), ticket.id());
        if (item != null) state = switch(item.workState()) { case "IN_PROGRESS" -> "IN_PROGRESS"; case "PENDING" -> "PENDING"; default -> reopened > 0 ? "REOPENED" : "ASSIGNED"; };
        var classifications=jdbc.query("select * from servicedesk_review where ticket_id=?",(row,index) ->
            new TicketReview.Classification(row.getString("category"),row.getString("priority"),row.getString("priority"),false,
                "agent","COORDINATOR",row.getString("impact"),row.getString("urgency"),row.getString("reviewed_by"),
                row.getTimestamp("reviewed_at").toInstant()),ticket.id());
        long closedWork=jdbc.queryForObject("select worked_millis from servicedesk_ticket where id=?",Long.class,ticket.id());
        return new TicketReview.View(ticket,state,task==null?null:task.id(),task==null?null:task.assignee(),
            estimates.isEmpty()?null:estimates.get(0),item==null?null:item.workState(),item==null?closedWork/60_000:item.workedMinutes(),
            item==null?null:item.remainingMinutes(),reopened,classifications.isEmpty()?null:classifications.get(0));
    }

    @Override public QueueItem project(String workspace, WorkExecution.Item execution) {
        return projectBatch(workspace, List.of(execution)).get(0);
    }

    @Override public List<QueueItem> projectBatch(String workspace, List<WorkExecution.Item> executions) {
        if (executions.isEmpty()) return List.of();
        var parameters = new ArrayList<Object>();
        parameters.add(workspace);
        parameters.add(ServiceDeskProcess.REVIEWED_KEY);
        executions.forEach(item -> parameters.add(item.task().processInstanceId()));
        var rows = jdbc.queryForList("select t.process_instance_id,t.title,t.worked_millis,r.priority,r.estimate_minutes,r.estimate_basis,"
            + " (select greatest(count(distinct h.review_task_id)-1,0) from servicedesk_estimate_history h where h.ticket_id=t.id) as reopened"
            + " from servicedesk_ticket t join servicedesk_review r on r.ticket_id=t.id where t.workspace=? and t.process_key=?"
            + " and t.process_instance_id in (" + String.join(",", Collections.nCopies(executions.size(), "?")) + ")", parameters.toArray());
        var byProcess = new HashMap<String,Map<String,Object>>();
        rows.forEach(row -> byProcess.put((String)row.get("process_instance_id"),row));
        return executions.stream().map(item -> {
            var row = byProcess.get(item.task().processInstanceId());
            if (row == null) throw CapabilityException.conflict("Reviewed work projection required");
            return projected(item, row);
        }).toList();
    }

    private QueueItem projected(WorkExecution.Item execution, Map<String,Object> row) {
        int minutes=((Number)row.get("estimate_minutes")).intValue();
        long worked=(execution.workedMillis()+((Number)row.get("worked_millis")).longValue())/60_000;
        long remaining="NOT_STARTED".equals(execution.workState()) ? minutes : Math.max(10,minutes-worked);
        if ("PENDING".equals(execution.workState())) remaining=(remaining+1)/2;
        int reopened=((Number)row.get("reopened")).intValue();
        return new QueueItem(execution.task(),(String)row.get("title"),execution.workState(),(String)row.get("priority"),
            (String)row.get("priority"),new QueueItem.Estimate(minutes,(String)row.get("estimate_basis")),worked,remaining,reopened,
            execution.pendingReason(),execution.pendingNote());
    }
}
