package vn.haui.dx.core.workflow.internal;

import java.util.List;
import org.flowable.engine.ProcessEngine;
import org.springframework.jdbc.core.JdbcTemplate;

/** Engine corruption and atomicity probes stay with the engine owner. Test code only. */
public class WorkflowStateFixture {
    private final JdbcTemplate jdbc;
    private final ProcessEngine engine;
    public WorkflowStateFixture(JdbcTemplate jdbc, ProcessEngine engine) { this.jdbc = jdbc; this.engine = engine; }
    public long[] counts() {
        return List.of("process_scope", "act_hi_procinst", "act_ru_task", "audit_event").stream()
            .mapToLong(table -> jdbc.queryForObject("select count(*) from " + table, Long.class)).toArray();
    }
    public void suspend(String id) { engine.getRuntimeService().suspendProcessInstanceById(id); }
    public void elapse(String taskId,long millis) { engine.getTaskService().setVariableLocal(taskId,"executionStarted",System.currentTimeMillis()-millis); }
    public void activate(String id) { engine.getRuntimeService().activateProcessInstanceById(id); }
    public void cancel(String id) { engine.getRuntimeService().deleteProcessInstance(id, "synthetic test cancellation"); }
    public void removeHistory(String id) { engine.getHistoryService().deleteHistoricProcessInstance(id); }
    public void changeWorkspace(String id, String workspace) { jdbc.update("update process_scope set workspace=? where id=?", workspace, id); }
    public boolean matches(String id, String workspace, String owner, String businessKey) {
        return jdbc.queryForObject("select count(*) from process_scope where id=? and workspace=? and owner=? and business_key=?",
            Long.class, id, workspace, owner, businessKey) == 1;
    }
}
