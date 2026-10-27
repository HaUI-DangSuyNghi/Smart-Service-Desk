package vn.haui.dx.hpdi.human.internal;

import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.haui.dx.hpdi.common.api.CapabilityException;
import vn.haui.dx.hpdi.human.api.*;

@Service
public class AgentDutyService implements AgentDuty {
    private final JdbcTemplate jdbc;
    private final HumanContext humans;
    private final PermissionGuard permissions;
    private final AgentDirectory directory;
    private final ActivityJournal audit;

    public AgentDutyService(JdbcTemplate jdbc, HumanContext humans, PermissionGuard permissions,
                            AgentDirectory directory, ActivityJournal audit) {
        this.jdbc = jdbc; this.humans = humans; this.permissions = permissions;
        this.directory = directory; this.audit = audit;
    }

    public List<Status> list(String workspace) {
        var actor = humans.currentActor(workspace);
        if (actor.hasRole("coordinator")) permissions.require(workspace, Permissions.TASK_ASSIGN);
        else {
            if (!actor.hasRole("agent")) throw CapabilityException.forbidden("Agent required");
            permissions.require(workspace, Permissions.TASK_HANDLE);
        }
        var agents = directory.currentAgents(workspace, null);
        var statuses = new java.util.HashMap<String, Status>();
        jdbc.query("select subject,on_duty,note,updated_at from hpdi_agent_duty where workspace=?",
            (org.springframework.jdbc.core.RowCallbackHandler) row -> statuses.put(row.getString("subject"),
                new Status(row.getString("subject"), row.getBoolean("on_duty"), row.getString("note"), row.getTimestamp("updated_at").toInstant())), workspace);
        return agents.stream().filter(agent -> actor.hasRole("coordinator") || actor.subject().equals(agent.subject()))
            .map(agent -> statuses.getOrDefault(agent.subject(), new Status(agent.subject(), false, null, null))).toList();
    }

    @Transactional
    public Status set(String workspace, String subject, Change change) {
        var actor = humans.currentActor(workspace);
        String target = "me".equals(subject) ? actor.subject() : subject;
        if (!actor.subject().equals(target) || !actor.hasRole("agent"))
            throw CapabilityException.forbidden("Agents may only change their own duty");
        permissions.require(workspace, Permissions.TASK_HANDLE);
        if (target == null || target.length() > 100 || change == null || change.onDuty() == null
                || (change.note() != null && change.note().length() > 500))
            throw CapabilityException.validation("Invalid duty change");
        if (directory.currentAgents(workspace, null).stream().noneMatch(agent -> target.equals(agent.subject())))
            throw CapabilityException.forbidden("Current agent membership required");
        var status = jdbc.queryForObject("insert into hpdi_agent_duty(workspace,subject,on_duty,note) values(?,?,?,?)"
            + " on conflict(workspace,subject) do update set on_duty=excluded.on_duty,note=excluded.note,updated_at=clock_timestamp()"
            + " returning subject,on_duty,note,updated_at",
            (row, index) -> new Status(row.getString("subject"), row.getBoolean("on_duty"), row.getString("note"), row.getTimestamp("updated_at").toInstant()),
            workspace, target, change.onDuty(), change.note());
        audit.record(workspace, new BusinessActivity("human.duty.changed", "human.agent", target,
            Map.of("subjectId", target, "dutyKey", change.onDuty() ? "on" : "off")));
        return status;
    }

    public boolean onDuty(String workspace, String subject) {
        var actor = humans.currentActor(workspace);
        if (!actor.subject().equals(subject) && !actor.hasRole("coordinator"))
            throw CapabilityException.forbidden("Coordinator required");
        permissions.require(workspace, actor.subject().equals(subject) ? Permissions.TASK_HANDLE : Permissions.TASK_ASSIGN);
        return Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from hpdi_agent_duty where workspace=? and subject=? and on_duty)",
            Boolean.class, workspace, subject));
    }
}
