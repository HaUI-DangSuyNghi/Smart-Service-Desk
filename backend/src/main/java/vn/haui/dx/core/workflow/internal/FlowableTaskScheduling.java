package vn.haui.dx.core.workflow.internal;
import java.util.Date;
import java.util.function.Supplier;
import org.flowable.engine.ProcessEngine;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.haui.dx.core.workflow.api.TaskScheduling;

@Service
public class FlowableTaskScheduling implements TaskScheduling {
    private final ProcessEngine engine;
    private final JdbcTemplate jdbc;
    private final ProcessRegistry registry;
    public FlowableTaskScheduling(ProcessEngine engine, JdbcTemplate jdbc,
            @org.springframework.context.annotation.Lazy ProcessRegistry registry) {
        this.engine = engine; this.jdbc = jdbc; this.registry = registry;
    }
    @Transactional
    public boolean reconcile(String workspace, String processId, String taskId, Supplier<Schedule> change) {
        var scopes = jdbc.queryForList("select process_key from process_scope where workspace=? and id=? for update", workspace, processId);
        if (scopes.isEmpty()) return false;
        var task = engine.getTaskService().createTaskQuery().taskId(taskId).processInstanceId(processId).singleResult();
        if (task == null || task.isSuspended() || !registry.supportsDefinition(task.getProcessDefinitionId())
                || !registry.executionTask((String) scopes.get(0).get("process_key"), task.getTaskDefinitionKey())) return false;
        var schedule = change.get();
        if (schedule != null) {
            if (task.getPriority() != schedule.priority()) engine.getTaskService().setPriority(taskId, schedule.priority());
            var due = Date.from(schedule.dueAt());
            if (!due.equals(task.getDueDate())) engine.getTaskService().setDueDate(taskId, due);
        }
        return true;
    }
}
