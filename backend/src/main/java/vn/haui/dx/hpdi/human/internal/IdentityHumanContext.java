package vn.haui.dx.hpdi.human.internal;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import vn.haui.dx.core.identity.api.WorkspaceAccess;
import vn.haui.dx.hpdi.human.api.Actor;
import vn.haui.dx.hpdi.human.api.HumanContext;
@Service
public class IdentityHumanContext implements HumanContext {
    private final WorkspaceAccess access;
    private final vn.haui.dx.core.identity.api.WorkspaceDirectory directory;
    public IdentityHumanContext(WorkspaceAccess access, vn.haui.dx.core.identity.api.WorkspaceDirectory directory) {
        this.access = access; this.directory = directory;
    }
    public Actor currentMember(String workspace) {
        var actor = currentActor(workspace);
        directory.refresh(workspace);
        var roles = directory.members(workspace).stream().filter(member -> member.sub().equals(actor.subject()))
            .flatMap(member -> member.roles().stream()).map(role -> role.value()).filter(actor.roles()::contains)
            .collect(Collectors.toUnmodifiableSet());
        if (roles.isEmpty()) throw vn.haui.dx.hpdi.common.api.CapabilityException.forbidden("Current workspace membership required");
        return new Actor(actor.subject(), workspace, roles, actor.capabilities());
    }
    public Actor currentActor(String workspace) {
        access.requireMember(workspace);
        var user = access.current();
        var roles = user.memberships().stream().filter(m -> m.workspace().equals(workspace))
            .map(m -> m.role().value()).collect(Collectors.toUnmodifiableSet());
        var capabilities = roles.stream().map(role -> "workspace.role:" + role)
            .collect(Collectors.toUnmodifiableSet());
        return new Actor(user.sub(), workspace, roles, capabilities);
    }
}
