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
    public ServiceDeskProcess(org.springframework.jdbc.core.JdbcTemplate jdbc,
            org.springframework.beans.factory.ObjectProvider<vn.haui.dx.hpdi.process.api.WorkExecution> execution,
            org.springframework.beans.factory.ObjectProvider<vn.haui.dx.apps.servicedesk.intake.IntakeAdmission> intake) {
        this.jdbc = jdbc; this.execution = execution; this.intake = intake;
    }

    @Override public Map<String, Set<String>> executionTasks() { return Map.of(REVIEWED_KEY, Set.of("handle"), INTAKE_KEY, Set.of("handle")); }

    @Override public void beforeComplete(String workspace, String processKey, String processId, String taskKey, String taskId, String outcome) {
        if (INTAKE_KEY.equals(processKey) && "intake".equals(taskKey)) intake.getObject().beforeIntakeComplete(workspace, processId, taskId, outcome);
        if (INTAKE_KEY.equals(processKey) && "follower".equals(taskKey)) throw vn.haui.dx.hpdi.common.api.CapabilityException.conflict("Canonical disposition API required");
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
            Set.of("agent", "coordinator", "assessment-worker"), Set.of("agent", "coordinator", "assessment-worker")),
            new ProcessDefinition(REVIEWED_KEY, Set.of("requester", "agent", "coordinator"), Set.of(),
            Map.of("review", Set.of("approved"), "handle", Set.of("resolved"), "confirm", Set.of("accepted", "rework")),
            Set.of("agent", "coordinator", "assessment-worker"), Set.of("agent", "coordinator", "assessment-worker")),
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
}
