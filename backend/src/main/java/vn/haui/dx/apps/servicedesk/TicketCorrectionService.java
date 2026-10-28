package vn.haui.dx.apps.servicedesk;

import java.sql.Timestamp;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import jakarta.validation.Validator;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.haui.dx.hpdi.common.api.CapabilityException;
import vn.haui.dx.hpdi.human.api.*;
import vn.haui.dx.hpdi.process.api.*;

@Service
public class TicketCorrectionService {
    private final JdbcTemplate jdbc;
    private final TicketService tickets;
    private final TicketReviewService reviews;
    private final HumanContext humans;
    private final PermissionGuard permissions;
    private final ProcessCapability processes;
    private final WorkExecution execution;
    private final WorkScheduling scheduling;
    private final EstimateInvalidation estimates;
    private final ActivityJournal audit;
    private final WorkAlerts alerts;
    private final TicketSlaService sla;
    private final Validator validator;

    public TicketCorrectionService(JdbcTemplate jdbc, TicketService tickets, TicketReviewService reviews,
            HumanContext humans, PermissionGuard permissions, ProcessCapability processes, WorkExecution execution,
            WorkScheduling scheduling, EstimateInvalidation estimates, ActivityJournal audit, WorkAlerts alerts,
            TicketSlaService sla, Validator validator) {
        this.jdbc=jdbc; this.tickets=tickets; this.reviews=reviews; this.humans=humans; this.permissions=permissions;
        this.processes=processes; this.execution=execution; this.scheduling=scheduling; this.estimates=estimates;
        this.audit=audit; this.alerts=alerts; this.sla=sla; this.validator=validator;
    }

    @Transactional
    public TicketReview.View correct(String workspace, UUID id, TicketCorrection.Input input) {
        if (!humans.currentActor(workspace).hasRole("agent", "coordinator"))
            throw CapabilityException.forbidden("Agent or Coordinator required");
        if (input == null || !validator.validate(input).isEmpty() || input.category()==null && input.priority()==null)
            throw CapabilityException.validation("Category or priority and a nonblank reason are required");
        var ticket = tickets.get(workspace, id);
        if (!ServiceDeskProcess.reviewed(ticket.process().processKey())) throw CapabilityException.conflict("Reviewed workflow required");
        var timeline = processes.timeline(workspace, ticket.process().processInstanceId());
        if (timeline.ended() || timeline.currentTasks().size()!=1 || !"handle".equals(timeline.currentTasks().get(0).key()))
            throw CapabilityException.conflict("Active approved handle required");
        String taskId = timeline.currentTasks().get(0).id();
        boolean active = scheduling.reconcile(workspace, ticket.process().processInstanceId(), taskId, () -> {
            var actor = humans.currentMember(workspace);
            String role = actor.hasRole("coordinator") ? "coordinator" : "agent";
            permissions.requireCurrent(workspace, role, Permissions.TASK_HANDLE);
            if ("coordinator".equals(role)) permissions.requireCurrent(workspace, role, Permissions.TASK_ASSIGN);
            if (ticket.creator().equals(actor.subject())) throw CapabilityException.forbidden("Independent corrector required");
            var work = execution.get(workspace, taskId);
            if (!actor.hasRole("coordinator") && !actor.subject().equals(work.task().assignee()))
                throw CapabilityException.forbidden("Active handle assignee required");
            var rows = jdbc.queryForList("select r.* from servicedesk_ticket t join servicedesk_review r on r.ticket_id=t.id"
                + " where t.workspace=? and t.id=? and not t.awaiting_review for update of t,r", workspace, id);
            if (rows.isEmpty()) throw CapabilityException.conflict("Current review required");
            boolean held = Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from servicedesk_intake_admission a"
                + " join servicedesk_ticket t on t.id=a.ticket_id where a.workspace=? and a.ticket_id=?"
                + " and (a.status<>'REVIEWED' or a.sealed_revision is distinct from t.context_revision))"
                + " or exists(select 1 from servicedesk_canonical_link where workspace=? and state='ACTIVE' and (follower_id=? or root_id=?))",
                Boolean.class, workspace, id, workspace, id, id));
            if (held) throw CapabilityException.conflict("Intake or canonical disposition prevents correction");
            var row = rows.get(0);
            String previousCategory = (String)row.get("category");
            var oldSla = sla.read(workspace, id);
            if (oldSla == null || !taskId.equals(oldSla.taskId())) throw CapabilityException.conflict("Current SLA generation required");
            String previousPriority = (String)row.get("priority");
            String category = input.category()==null ? previousCategory : input.category().name();
            String priority = input.priority()==null ? previousPriority : input.priority().name();
            boolean categoryChanged = !category.equals(previousCategory);
            boolean priorityChanged = !priority.equals(previousPriority);
            if (!categoryChanged && !priorityChanged) throw CapabilityException.conflict("Classification is unchanged");
            var operation = UUID.randomUUID();
            var at = Timestamp.from(sla.now());
            String reviewId = (String)row.get("review_task_id");
            if (categoryChanged) history(operation, workspace, id, ticket, reviewId, taskId, "category", previousCategory, category, actor.subject(), input.reason(), at);
            if (priorityChanged) {
                history(operation, workspace, id, ticket, reviewId, taskId, "priority", previousPriority, priority, actor.subject(), input.reason(), at);
                sla.correctPriority(workspace, id, taskId, priority, operation);
            }
            int minutes = "DEFAULT".equals(row.get("estimate_basis"))
                ? TicketReview.defaultMinutes(TicketReview.Category.valueOf(category), TicketReview.Priority.valueOf(priority))
                : ((Number)row.get("estimate_minutes")).intValue();
            jdbc.update("update servicedesk_review set category=?,priority=?,estimate_minutes=?,estimate_generation=? where ticket_id=?",
                category, input.priority()==null ? row.get("priority") : priority, minutes, operation.toString(), id);
            jdbc.update("update servicedesk_alert_monitor set category=? where workspace=? and handle_task_id=?", category, workspace, taskId);
            jdbc.update("insert into servicedesk_estimate_history(ticket_id,review_task_id,category,priority,assignee,minutes,basis,changed_by,reason)"
                + " values(?,?,?,?,?,?,?,?,?)", id, reviewId, category, priority, work.task().assignee(), minutes, row.get("estimate_basis"), actor.subject(), input.reason());
            estimates.classificationChanged(workspace, taskId, actor.subject());
            boolean groupChanged = categoryChanged && !Objects.equals(TicketAssignmentEligibility.supportGroup(TicketReview.Category.valueOf(previousCategory)),
                TicketAssignmentEligibility.supportGroup(TicketReview.Category.valueOf(category)));
            if (groupChanged) {
                if (!"NOT_STARTED".equals(work.workState()))
                    jdbc.update("update servicedesk_review set correction_incumbent=? where ticket_id=?", work.task().assignee(), id);
            }
            boolean criticalCorrection = priorityChanged && "CRITICAL".equals(priority);
            if (groupChanged || criticalCorrection) {
                alerts.publish(new WorkAlerts.Event(workspace, WorkAlerts.Type.CLASSIFICATION_CHANGED,
                    criticalCorrection ? WorkAlerts.Severity.CRITICAL : WorkAlerts.Severity.WARNING,
                    ticket.process().processInstanceId(), ticket.process().processKey(), id.toString(), taskId,
                    operation.toString(), criticalCorrection ? "Priority corrected to CRITICAL; Coordinator review required"
                        : "Support group changed; Coordinator review required", true, null));
            }
            if (groupChanged && "NOT_STARTED".equals(work.workState())) execution.returnForCorrection(workspace, taskId, input.reason());
            audit.record(workspace, new BusinessActivity("servicedesk.ticket.classification.correct", "servicedesk.ticket", id.toString(),
                Map.of("operationId", operation.toString(), "processInstanceId", ticket.process().processInstanceId(),
                    "reviewTaskId", reviewId, "handleTaskId", taskId)));
            return null;
        });
        if (!active) throw CapabilityException.conflict("Handle generation changed");
        return reviews.view(workspace, tickets.get(workspace, id));
    }

    private void history(UUID operation, String workspace, UUID id, Ticket ticket, String reviewId, String taskId,
            String field, String previous, String next, String actor, String reason, Timestamp at) {
        jdbc.update("insert into servicedesk_classification_correction(operation_id,workspace,ticket_id,process_instance_id,"
            + "review_task_id,handle_task_id,field,previous_value,new_value,changed_by,reason,changed_at) values(?,?,?,?,?,?,?,?,?,?,?,?)",
            operation, workspace, id, ticket.process().processInstanceId(), reviewId, taskId, field, previous, next, actor, reason, at);
    }
}
