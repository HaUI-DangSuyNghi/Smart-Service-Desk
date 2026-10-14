package vn.haui.dx.core.identity.web;

import java.util.Comparator;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.haui.dx.core.identity.api.CurrentUser;
import vn.haui.dx.core.identity.api.Membership;
import vn.haui.dx.core.identity.api.WorkspaceAccess;

@RestController
public class MeController {
    private final WorkspaceAccess access;
    public MeController(WorkspaceAccess access) { this.access = access; }

    public record Me(String sub, String username, List<Membership> memberships, boolean platformAdmin) {}

    @GetMapping("/api/core/v1/me")
    public Me me() {
        CurrentUser user = access.current();
        List<Membership> memberships = user.memberships().stream()
            .sorted(Comparator.comparing(Membership::workspace).thenComparing(membership -> membership.role().value())).toList();
        return new Me(user.sub(), user.username(), memberships, user.platformAdmin());
    }
}
