package vn.haui.dx.core.identity.internal;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.security.oauth2.jwt.Jwt;
import vn.haui.dx.core.identity.api.CurrentUser;
import vn.haui.dx.core.identity.api.Membership;
import vn.haui.dx.core.identity.api.WorkspaceRole;

/** CurrentUser dựng từ JWT đã kiểm chữ ký. */
record JwtCurrentUser(String sub, String username, Set<Membership> memberships, boolean platformAdmin) implements CurrentUser {
    /** Chỉ nhận group đúng mẫu; chuỗi khác (group phẳng, "..", ký tự lạ) bị bỏ qua. */
    static final Pattern GROUP = Pattern.compile("^/workspaces/([a-z0-9-]{1,50})/([a-z0-9-]{1,30})$");
    static final String PLATFORM_ADMIN = "platform-admin";

    JwtCurrentUser { memberships = Set.copyOf(memberships); }

    static JwtCurrentUser from(Jwt jwt) {
        Set<Membership> memberships = new HashSet<>();
        if (jwt.getClaim("groups") instanceof Collection<?> groups) {
            for (Object group : groups) {
                Matcher match = group instanceof String text ? GROUP.matcher(text) : null;
                if (match != null && match.matches()) memberships.add(new Membership(match.group(1), new WorkspaceRole(match.group(2))));
            }
        }
        boolean admin = jwt.getClaim("realm_access") instanceof Map<?, ?> access
            && access.get("roles") instanceof Collection<?> roles && roles.contains(PLATFORM_ADMIN);
        String username = jwt.getClaimAsString("preferred_username");
        return new JwtCurrentUser(jwt.getSubject(), username == null ? jwt.getSubject() : username, memberships, admin);
    }

    @Override public boolean isMember(String workspace) {
        return memberships.stream().anyMatch(membership -> membership.workspace().equals(workspace));
    }
    @Override public boolean hasRole(String workspace, WorkspaceRole... anyOf) {
        List<WorkspaceRole> wanted = List.of(anyOf);
        return memberships.stream().anyMatch(membership -> membership.workspace().equals(workspace) && wanted.contains(membership.role()));
    }
}
