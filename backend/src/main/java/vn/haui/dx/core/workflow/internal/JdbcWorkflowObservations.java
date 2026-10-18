package vn.haui.dx.core.workflow.internal;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.haui.dx.core.common.CoreException;
import vn.haui.dx.core.identity.api.WorkspaceAccess;
import vn.haui.dx.core.identity.api.WorkspaceRole;
import vn.haui.dx.core.workflow.api.*;

@Service
public class JdbcWorkflowObservations implements WorkflowObservations {
    private final NamedParameterJdbcTemplate jdbc;
    private final WorkspaceAccess access;
    public JdbcWorkflowObservations(JdbcTemplate jdbc, WorkspaceAccess access) {
        this.jdbc = new NamedParameterJdbcTemplate(jdbc); this.access = access;
    }
    @Transactional(readOnly=true, isolation=org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public List<ProcessSnapshot> snapshots(String workspace, Set<String> ids) {
        access.requireMember(workspace);
        if (ids == null || ids.size() > 1000 || ids.stream().anyMatch(id -> id == null || id.isBlank() || id.length()>100))
            throw CoreException.validation("Invalid process observation batch");
        if (ids.isEmpty()) return List.of();
        var user = access.current();
        var parameters = Map.<String,Object>of("workspace", workspace, "ids", ids, "actor", user.sub(),
            "staff", user.hasRole(workspace, WorkspaceRole.AGENT, WorkspaceRole.COORDINATOR));
        var rows = jdbc.queryForList("""
            select s.*, h.proc_inst_id_ as history_id, h.end_time_, h.delete_reason_, h.end_act_id_
            from process_scope s left join act_hi_procinst h on h.proc_inst_id_=s.id
            where s.workspace=:workspace and s.id in (:ids) and (:staff=true or s.owner=:actor)
            """, parameters);
        if (rows.isEmpty()) return List.of();
        var authorized = rows.stream().map(r -> (String) r.get("id")).toList();
        var tasks = new HashMap<String,List<ProcessSnapshot.TaskState>>();
        jdbc.query("select proc_inst_id_,task_def_key_,suspension_state_ from act_ru_task where proc_inst_id_ in (:ids)",
            Map.of("ids", authorized), (org.springframework.jdbc.core.RowCallbackHandler) row ->
                tasks.computeIfAbsent(row.getString("proc_inst_id_"), key -> new ArrayList<>()).add(
                    new ProcessSnapshot.TaskState(row.getString("task_def_key_"), row.getInt("suspension_state_")==1)));
        return rows.stream().map(r -> new ProcessSnapshot((String)r.get("id"), (String)r.get("workspace"),
            (String)r.get("owner"), (String)r.get("process_key"), (String)r.get("business_key"),
            r.get("history_id") != null, r.get("end_time_") == null ? null : ((java.sql.Timestamp)r.get("end_time_")).toInstant(),
            (String)r.get("delete_reason_"), (String)r.get("end_act_id_"), tasks.getOrDefault((String)r.get("id"), List.of()))).toList();
    }
}
