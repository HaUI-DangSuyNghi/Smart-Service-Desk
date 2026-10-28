package vn.haui.dx.hpdi.human.internal;

import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Service;
import vn.haui.dx.core.identity.api.ServiceIdentity;
import vn.haui.dx.core.identity.api.WorkspaceDirectory;
import vn.haui.dx.hpdi.human.api.*;

@Service
public class WorkspaceAlertProcessing implements AlertProcessing {
    private final ServiceIdentity identity;
    private final PermissionGuard permissions;
    private final WorkspaceDirectory directory;
    private final AgentScheduleService schedules;
    public WorkspaceAlertProcessing(ServiceIdentity identity, PermissionGuard permissions, WorkspaceDirectory directory,
            AgentScheduleService schedules) {
        this.identity = identity; this.permissions = permissions; this.directory = directory;
        this.schedules = schedules;
    }
    public void run(String workspace, Runnable operation) { identity.run(workspace, "alerts:process", operation); }
    @org.springframework.transaction.annotation.Transactional(readOnly=true, isolation=org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public Map<String, Boolean> duty(String workspace, String group) {
        permissions.require(workspace, "alerts:process");
        directory.refresh(workspace);
        var supported = group == null ? null : directory.supportGroupMembers(workspace, group);
        var result = new HashMap<String, Boolean>();
        directory.members(workspace).stream().filter(member -> member.roles().stream().anyMatch(role -> "agent".equals(role.value())))
            .filter(member -> supported == null || supported.contains(member.sub())).forEach(member -> result.put(member.sub(), false));
        result.replaceAll((subject,manual) -> schedules.evaluate(workspace,subject).onDuty());
        return Map.copyOf(result);
    }
}
