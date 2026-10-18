package vn.haui.dx.hpdi.human.internal;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import vn.haui.dx.core.identity.api.WorkspaceAccess;
import vn.haui.dx.hpdi.human.api.Actor;
import vn.haui.dx.hpdi.human.api.HumanContext;
@Service
public class IdentityHumanContext implements HumanContext {
    private final WorkspaceAccess access;
    public IdentityHumanContext(WorkspaceAccess access) { this.access = access; }
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
