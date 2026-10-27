package vn.haui.dx.apps.servicedesk;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import vn.haui.dx.hpdi.common.api.CapabilityException;
import vn.haui.dx.hpdi.process.api.WorkEstimate;
import vn.haui.dx.hpdi.process.api.WorkItem;

@Component
public class TicketWorkEstimate implements WorkEstimate {
    private final JdbcTemplate jdbc;
    private final TicketAssignmentEligibility eligibility;
    public TicketWorkEstimate(JdbcTemplate jdbc, TicketAssignmentEligibility eligibility) {
        this.jdbc = jdbc;
        this.eligibility = eligibility;
    }
    public boolean supports(String key) { return ServiceDeskProcess.reviewed(key); }
    public Snapshot current(String workspace, WorkItem task, String agent) {
        var rows = jdbc.queryForList("select t.title,t.creator,r.* from servicedesk_ticket t join servicedesk_review r on r.ticket_id=t.id"
            + " where t.workspace=? and t.process_instance_id=? and not t.awaiting_review", workspace, task.processInstanceId());
        if (rows.isEmpty() || !"handle".equals(task.key())) throw CapabilityException.conflict("Active reviewed handle required");
        var row = rows.get(0);
        eligibility.require(workspace, TicketReview.Category.valueOf((String) row.get("category")), (String) row.get("creator"), agent);
        return new Snapshot((String) row.get("review_task_id"), (String) row.get("title"), ((Number) row.get("estimate_minutes")).intValue());
    }
    public void approve(String workspace, WorkItem task, String generation, int minutes, String actor, String reason) {
        int changed = jdbc.update("update servicedesk_review r set estimate_minutes=?,estimate_basis='COORDINATOR'"
            + " from servicedesk_ticket t where t.id=r.ticket_id and t.workspace=? and t.process_instance_id=? and r.review_task_id=?",
            minutes, workspace, task.processInstanceId(), generation);
        if (changed != 1) throw CapabilityException.conflict("Estimate generation changed");
        jdbc.update("insert into servicedesk_estimate_history(ticket_id,review_task_id,category,priority,assignee,minutes,basis,changed_by,reason)"
            + " select r.ticket_id,r.review_task_id,r.category,r.priority,?,?,'COORDINATOR',?,?"
            + " from servicedesk_review r join servicedesk_ticket t on t.id=r.ticket_id where t.workspace=? and t.process_instance_id=?",
            task.assignee(), minutes, actor, reason, workspace, task.processInstanceId());
    }
}
