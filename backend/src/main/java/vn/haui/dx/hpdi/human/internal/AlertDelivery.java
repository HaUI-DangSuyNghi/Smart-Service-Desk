package vn.haui.dx.hpdi.human.internal;

import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.haui.dx.core.identity.api.WorkspaceDirectory;
import vn.haui.dx.hpdi.human.api.*;

@Service
public class AlertDelivery {
    private final JdbcTemplate jdbc;
    private final WorkspaceDirectory directory;
    private final ActivityJournal audit;
    public AlertDelivery(JdbcTemplate jdbc, WorkspaceDirectory directory, ActivityJournal audit) {
        this.jdbc = jdbc; this.directory = directory; this.audit = audit;
    }
    public Set<String> recipients(String workspace, boolean coordinators, String agent) {
        directory.refresh(workspace);
        var grants = directory.rolePermissions(workspace);
        var result = new HashSet<String>();
        for (var member : directory.members(workspace)) {
            var roles = member.roles().stream().map(role -> role.value()).collect(java.util.stream.Collectors.toSet());
            var allowed = roles.stream().flatMap(role -> grants.getOrDefault(role, List.of()).stream()).map(grant -> grant.name())
                .collect(java.util.stream.Collectors.toSet());
            if (coordinators && roles.contains("coordinator") && allowed.contains(Permissions.REQUEST_VIEW_ALL)
                    || member.sub().equals(agent) && roles.contains("agent") && allowed.contains(Permissions.TASK_HANDLE)) result.add(member.sub());
        }
        return Set.copyOf(result);
    }
    @Transactional
    public void deliver(String workspace, UUID id, Set<String> recipients) {
        var rows = jdbc.queryForList("select * from hpdi_alert_event where workspace=? and id=? and delivered_at is null and (not agent_suppressed or coordinators) for update", workspace, id);
        if (rows.isEmpty() || recipients.isEmpty()) return;
        var event = rows.get(0);
        var current = recipients(workspace, (Boolean) event.get("coordinators"), Boolean.TRUE.equals(event.get("agent_suppressed")) ? null : (String) event.get("agent"));
        for (String subject : recipients) {
            if (current.contains(subject)) jdbc.update("insert into hpdi_alert_recipient(workspace,event_id,subject) values(?,?,?) on conflict do nothing", workspace, id, subject);
        }
        jdbc.update("update hpdi_alert_event set delivered_at=current_timestamp where workspace=? and id=?", workspace, id);
        audit.recordSystem(workspace, new BusinessActivity("human.alert.delivered", "human.alert", id.toString(), Map.of()));
    }
}
