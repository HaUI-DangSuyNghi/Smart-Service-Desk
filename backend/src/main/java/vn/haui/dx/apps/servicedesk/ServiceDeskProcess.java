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
    public static final String INTAKE_KEY = "service-desk-intake-reviewed";
    public static boolean reviewed(String key) { return REVIEWED_KEY.equals(key) || INTAKE_KEY.equals(key); }
    private final org.springframework.jdbc.core.JdbcTemplate jdbc;
    private final org.springframework.beans.factory.ObjectProvider<vn.haui.dx.hpdi.process.api.WorkExecution> execution;
    private final org.springframework.beans.factory.ObjectProvider<vn.haui.dx.apps.servicedesk.intake.IntakeAdmission> intake;
    private final org.springframework.beans.factory.ObjectProvider<vn.haui.dx.apps.servicedesk.intake.CanonicalTicketService> canonical;
    private final org.springframework.beans.factory.ObjectProvider<TicketAssignmentEligibility> eligibility;
    private final org.springframework.beans.factory.ObjectProvider<TicketSlaService> sla;
    private final org.springframework.beans.factory.ObjectProvider<TicketAlerts> alerts;
    private final org.springframework.beans.factory.ObjectProvider<TicketConfirmation> confirmation;
    public ServiceDeskProcess(org.springframework.jdbc.core.JdbcTemplate jdbc,
            org.springframework.beans.factory.ObjectProvider<vn.haui.dx.hpdi.process.api.WorkExecution> execution,
            org.springframework.beans.factory.ObjectProvider<vn.haui.dx.apps.servicedesk.intake.IntakeAdmission> intake,
            org.springframework.beans.factory.ObjectProvider<vn.haui.dx.apps.servicedesk.intake.CanonicalTicketService> canonical,
            org.springframework.beans.factory.ObjectProvider<TicketAssignmentEligibility> eligibility,
            org.springframework.beans.factory.ObjectProvider<TicketSlaService> sla,
            org.springframework.beans.factory.ObjectProvider<TicketAlerts> alerts,
            org.springframework.beans.factory.ObjectProvider<TicketConfirmation> confirmation) {
        this.jdbc = jdbc; this.execution = execution; this.intake = intake; this.canonical = canonical; this.eligibility = eligibility; this.sla = sla;
        this.alerts = alerts;
        this.confirmation = confirmation;
    }

    @Override public void afterComplete(String workspace, String key, String processId,
            vn.haui.dx.hpdi.process.api.WorkItem completed, String outcome, List<vn.haui.dx.hpdi.process.api.WorkItem> active) {
        confirmation.getObject().completed(workspace, key, processId, completed, outcome, active);
    }
    @Override public String evaluateTimer(String workspace, String key, String processId, String businessKey,
            String owner, String generation, vn.haui.dx.hpdi.process.api.WorkItem active, java.time.Instant now) {
        return confirmation.getObject().evaluate(workspace, key, processId, businessKey, owner, generation, active, now);
    }

    @Override public void enrollTimer(String workspace, String key, String processId, String businessKey,
            String owner, String generation, java.time.Instant createdAt, String handleId, java.time.Instant resolvedAt, java.time.Instant now) {
        confirmation.getObject().enroll(workspace, key, processId, businessKey, owner, generation, createdAt, handleId, resolvedAt, now);
    }

    @Override public void afterExecution(String workspace, String processKey, String processId, String taskId,
            vn.haui.dx.hpdi.process.api.WorkExecution.Action action, String reason, java.time.Instant at) {
        if (reviewed(processKey)) sla.getObject().transition(workspace, processId, taskId, action, reason, at);
        if (reviewed(processKey)) alerts.getObject().execution(workspace, taskId, action, at);
    }

    @Override public void beforeAssign(String workspace, String processKey, String processId, String taskKey, String subject) {
        if (!reviewed(processKey) || !"handle".equals(taskKey)) return;
        var rows = jdbc.queryForList("select t.creator,r.category from servicedesk_ticket t join servicedesk_review r on r.ticket_id=t.id"
            + " where t.workspace=? and t.process_instance_id=?", workspace, processId);
        if (rows.isEmpty()) throw vn.haui.dx.hpdi.common.api.CapabilityException.conflict("Ticket review required");
        var row = rows.get(0);
        eligibility.getObject().require(workspace, TicketReview.Category.valueOf((String) row.get("category")), (String) row.get("creator"), subject);

    }

    @Override public Map<String, Set<String>> executionTasks() { return Map.of(REVIEWED_KEY, Set.of("handle"), INTAKE_KEY, Set.of("handle")); }

    @Override public String releaseTarget(String processKey, String taskKey) {
        return reviewed(processKey) && "handle".equals(taskKey) ? "review" : null;
    }
    @Override public void afterRelease(String workspace, String processKey, String processId, String taskId,
            String reviewId, long workedMillis, String actor, String reason, String note) {
        intake.getObject().handover(workspace, processId, reviewId, actor, reason, note);
        jdbc.update("insert into servicedesk_work_release(workspace,ticket_id,task_id,review_task_id,released_by,reason,note,worked_millis)"
            + " select workspace,id,?,?,?,?,?,? from servicedesk_ticket where workspace=? and process_instance_id=?",
            taskId, reviewId, actor, reason, note, workedMillis, workspace, processId);
        jdbc.update("update servicedesk_ticket set worked_millis=worked_millis+?,awaiting_review=true where workspace=? and process_instance_id=?",
            workedMillis, workspace, processId);
        jdbc.update("update servicedesk_review r set assignee=null from servicedesk_ticket t where t.id=r.ticket_id and t.workspace=? and t.process_instance_id=?", workspace, processId);
        sla.getObject().released(workspace, taskId);
        alerts.getObject().released(workspace, taskId);
    }

    @Override public void afterAssign(String workspace, String processKey, String processId, String taskKey, String subject) {
        if (!reviewed(processKey) || !"handle".equals(taskKey)) return;
        confirmation.getObject().received(workspace, processId, "ASSIGNED");
        jdbc.update("update servicedesk_review r set correction_incumbent=null from servicedesk_ticket t"
            + " where t.id=r.ticket_id and t.workspace=? and t.process_instance_id=?", workspace, processId);
        alerts.getObject().assigned(workspace, processId, subject);
        var group = jdbc.queryForObject("select r.category from servicedesk_review r join servicedesk_ticket t on t.id=r.ticket_id"
            + " where t.workspace=? and t.process_instance_id=?", String.class, workspace, processId);
        String supportGroup = TicketAssignmentEligibility.supportGroup(TicketReview.Category.valueOf(group));
        jdbc.update("insert into servicedesk_assignment_event(workspace,process_id,subject) values(?,?,?)", workspace, processId, subject);
        if (supportGroup != null)
            jdbc.update("insert into servicedesk_assignment_cursor(workspace,support_group,last_subject) values(?,?,?)"
                + " on conflict(workspace,support_group) do update set last_subject=excluded.last_subject", workspace, supportGroup, subject);
    }

    @Override public void beforeComplete(String workspace, String processKey, String processId, String taskKey, String taskId, String outcome) {
        if (reviewed(processKey) && "handle".equals(taskKey)) sla.getObject().resolved(workspace, taskId);
        if (reviewed(processKey) && "handle".equals(taskKey)) alerts.getObject().resolved(workspace, taskId);
        if (INTAKE_KEY.equals(processKey) && "intake".equals(taskKey)) {
            if ("duplicate".equals(outcome)) canonical.getObject().beforeLinkComplete(workspace, processId);
            else intake.getObject().beforeIntakeComplete(workspace, processId, taskId, outcome);
        }
        if (INTAKE_KEY.equals(processKey) && "follower".equals(taskKey)) canonical.getObject().beforeFollowerComplete(workspace, processId, outcome);
        if (reviewed(processKey) && "confirm".equals(taskKey) && "accepted".equals(outcome)) canonical.getObject().rootConfirmed(workspace, processId);
        if (reviewed(processKey) && "review".equals(taskKey)) intake.getObject().beforeComplete(workspace, processId, taskId);
        if (reviewed(processKey) && "confirm".equals(taskKey) && "rework".equals(outcome)) intake.getObject().rework(workspace, processId);
        if (reviewed(processKey) && "confirm".equals(taskKey) && "rework".equals(outcome))
            jdbc.update("update servicedesk_ticket set awaiting_review=? where workspace=? and process_instance_id=?",!INTAKE_KEY.equals(processKey),workspace,processId);
        if (reviewed(processKey) && "handle".equals(taskKey))
            jdbc.update("update servicedesk_ticket set worked_millis=worked_millis+? where workspace=? and process_instance_id=?",
                execution.getObject().get(workspace, taskId).workedMillis(), workspace, processId);
        if (reviewed(processKey) && "review".equals(taskKey)
                && !Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from servicedesk_review r"
                    + " join servicedesk_ticket t on t.id=r.ticket_id where t.workspace=?"
                    + " and t.process_instance_id=? and r.review_task_id=?)", Boolean.class, workspace, processId, taskId)))
            throw vn.haui.dx.hpdi.common.api.CapabilityException.conflict("Use the ticket review API before completing review");
    }

    // Không tự phục vụ: process phải ra đời cùng ticket qua API của app
    @Override public List<ProcessDefinition> definitions() {
        return List.of(new ProcessDefinition(INTAKE_KEY, Set.of("requester", "agent", "coordinator"), Set.of(),
            Map.of("intake", Set.of("ready", "duplicate"), "follower", Set.of("closed", "split"),
                "review", Set.of("approved"), "handle", Set.of("resolved"), "confirm", Set.of("accepted", "rework")),
            Set.of("agent", "coordinator", "assessment-worker"), Set.of("agent", "coordinator", "assessment-worker"))
                .withLabels(reviewLabels("Tiếp nhận và xử lý hỗ trợ CNTT", true)),
            new ProcessDefinition(REVIEWED_KEY, Set.of("requester", "agent", "coordinator"), Set.of(),
            Map.of("review", Set.of("approved"), "handle", Set.of("resolved"), "confirm", Set.of("accepted", "rework")),
            Set.of("agent", "coordinator", "assessment-worker"), Set.of("agent", "coordinator", "assessment-worker"))
                .withLabels(reviewLabels("Duyệt và xử lý hỗ trợ CNTT", false)),
            new ProcessDefinition(KEY, Set.of("requester", "agent", "coordinator"), Set.of(),
            Map.of("handle", Set.of("resolved"), "confirm", Set.of("accepted", "rework")),
            Set.of("agent", "coordinator", "assessment-worker"), Set.of("coordinator", "assessment-worker"))
            .withLabels(ProcessLabels.named("Yêu cầu hỗ trợ CNTT")
                .task("handle", "Xử lý yêu cầu")
                .task("confirm", "Xác nhận kết quả")
                .outcome("resolved", "Đã xử lý")
                .outcome("accepted", "Chấp nhận")
                .outcome("rework", "Yêu cầu làm lại")));
    }

    private ProcessLabels reviewLabels(String name, boolean intakeWorkflow) {
        var labels = ProcessLabels.named(name)
                .task("review", "Điều phối viên duyệt")
                .task("handle", "Xử lý yêu cầu")
                .task("confirm", "Người gửi xác nhận")
                .outcome("approved", "Đã duyệt")
                .outcome("resolved", "Đã xử lý")
                .outcome("accepted", "Chấp nhận")
                .outcome("rework", "Yêu cầu làm lại");
        return intakeWorkflow ? labels
                .task("intake", "Tiếp nhận thông tin")
                .task("follower", "Theo dõi sự cố đã liên kết")
                .outcome("ready", "Chuyển duyệt")
                .outcome("duplicate", "Theo dõi cùng sự cố")
                .outcome("closed", "Đã xác nhận kết quả")
                .outcome("split", "Tách để tiếp nhận độc lập") : labels;
    }
}
