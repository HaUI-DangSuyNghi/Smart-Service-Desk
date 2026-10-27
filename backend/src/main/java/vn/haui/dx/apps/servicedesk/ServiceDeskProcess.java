package vn.haui.dx.apps.servicedesk;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;
import vn.haui.dx.hpdi.process.api.ProcessContribution;
import vn.haui.dx.hpdi.process.api.ProcessDefinition;
import vn.haui.dx.hpdi.process.api.ProcessLabels;

@Component
public class ServiceDeskProcess implements ProcessContribution {
    public static final String KEY = "service-desk";
    public static final String REVIEWED_KEY = "service-desk-reviewed";
    private final org.springframework.jdbc.core.JdbcTemplate jdbc;
    private final org.springframework.beans.factory.ObjectProvider<vn.haui.dx.hpdi.process.api.WorkExecution> execution;
    private final org.springframework.beans.factory.ObjectProvider<TicketAssignmentEligibility> eligibility;
    public ServiceDeskProcess(org.springframework.jdbc.core.JdbcTemplate jdbc,
            org.springframework.beans.factory.ObjectProvider<vn.haui.dx.hpdi.process.api.WorkExecution> execution,
            org.springframework.beans.factory.ObjectProvider<TicketAssignmentEligibility> eligibility) {
        this.jdbc = jdbc; this.execution = execution; this.eligibility = eligibility;
    }

    @Override public void beforeAssign(String workspace, String processKey, String processId, String taskKey, String subject) {
        if (!REVIEWED_KEY.equals(processKey) || !"handle".equals(taskKey)) return;
        var rows = jdbc.queryForList("select t.creator,r.category from servicedesk_ticket t join servicedesk_review r on r.ticket_id=t.id"
            + " where t.workspace=? and t.process_instance_id=?", workspace, processId);
        if (rows.isEmpty()) throw vn.haui.dx.hpdi.common.api.CapabilityException.conflict("Ticket review required");
        var row = rows.get(0);
        eligibility.getObject().require(workspace, TicketReview.Category.valueOf((String) row.get("category")), (String) row.get("creator"), subject);
    }

    @Override public Map<String, Set<String>> executionTasks() { return Map.of(REVIEWED_KEY, Set.of("handle")); }

    @Override public void beforeComplete(String workspace, String processKey, String processId, String taskKey, String taskId, String outcome) {
        if (REVIEWED_KEY.equals(processKey) && "confirm".equals(taskKey) && "rework".equals(outcome))
            jdbc.update("update servicedesk_ticket set awaiting_review=true where workspace=? and process_instance_id=?",workspace,processId);
        if (REVIEWED_KEY.equals(processKey) && "handle".equals(taskKey))
            jdbc.update("update servicedesk_ticket set worked_millis=worked_millis+? where workspace=? and process_instance_id=?",
                execution.getObject().get(workspace, taskId).workedMillis(), workspace, processId);
        if (REVIEWED_KEY.equals(processKey) && "review".equals(taskKey)
                && !Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from servicedesk_review r"
                    + " join servicedesk_ticket t on t.id=r.ticket_id where t.workspace=?"
                    + " and t.process_instance_id=? and r.review_task_id=?)", Boolean.class, workspace, processId, taskId)))
            throw vn.haui.dx.hpdi.common.api.CapabilityException.conflict("Use the ticket review API before completing review");
    }

    // Không tự phục vụ: process phải ra đời cùng ticket qua API của app
    @Override public List<ProcessDefinition> definitions() {
        return List.of(new ProcessDefinition(REVIEWED_KEY, Set.of("requester", "agent", "coordinator"), Set.of(),
            Map.of("review", Set.of("approved"), "handle", Set.of("resolved"), "confirm", Set.of("accepted", "rework")),
            Set.of("agent", "coordinator"), Set.of("agent", "coordinator")),
            new ProcessDefinition(KEY, Set.of("requester", "agent", "coordinator"), Set.of(),
            Map.of("handle", Set.of("resolved"), "confirm", Set.of("accepted", "rework")),
            Set.of("agent", "coordinator"), Set.of("coordinator"))
            .withLabels(ProcessLabels.named("Yêu cầu hỗ trợ CNTT")
                .task("handle", "Xử lý yêu cầu")
                .task("confirm", "Xác nhận kết quả")
                .outcome("resolved", "Đã xử lý")
                .outcome("accepted", "Chấp nhận")
                .outcome("rework", "Yêu cầu làm lại")));
    }
}
