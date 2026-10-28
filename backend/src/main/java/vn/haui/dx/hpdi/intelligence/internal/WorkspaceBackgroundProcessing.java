package vn.haui.dx.hpdi.intelligence.internal;

import org.springframework.stereotype.Service;
import vn.haui.dx.core.identity.api.*;
import vn.haui.dx.hpdi.common.api.CapabilityException;
import vn.haui.dx.hpdi.intelligence.api.BackgroundProcessing;

@Service
public class WorkspaceBackgroundProcessing implements BackgroundProcessing {
    private final ServiceIdentity identity;
    private final WorkspaceAccess access;
    private final WorkspaceDirectory directory;
    private final WorkspacePermissions permissions;

    public WorkspaceBackgroundProcessing(ServiceIdentity identity, WorkspaceAccess access,
            WorkspaceDirectory directory, WorkspacePermissions permissions) {
        this.identity=identity; this.access=access; this.directory=directory; this.permissions=permissions;
    }
    @Override public void run(String workspace, Runnable operation) {
        try { identity.run(workspace,"intelligence:process",operation); }
        catch (vn.haui.dx.core.common.CoreException failure) {
            if (failure.code().status()==401 || failure.code().status()==403)
                throw CapabilityException.forbidden("Background processing authorization revoked");
            throw vn.haui.dx.hpdi.intelligence.api.IntelligenceFailure.unavailable();
        }
    }
    @Override public void authorize(String workspace, String requester) {
        directory.refresh(workspace);
        permissions.require(workspace,"intelligence:process");
        var members=directory.members(workspace);
        var grants=directory.rolePermissions(workspace);
        for (var requirement : java.util.List.of(java.util.Map.entry(access.current().sub(),"intelligence:process"),java.util.Map.entry(requester,"request:create"))) {
            boolean allowed=members.stream().filter(member -> member.sub().equals(requirement.getKey()))
                .flatMap(member -> member.roles().stream())
                .flatMap(role -> grants.getOrDefault(role.value(),java.util.List.of()).stream())
                .anyMatch(grant -> requirement.getValue().equals(grant.name()));
            if (!allowed) throw CapabilityException.forbidden("Background processing authorization revoked");
        }
    }
}
