package vn.haui.dx.core.identity.internal;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import vn.haui.dx.core.common.CoreException;
import vn.haui.dx.core.common.ErrorCode;
import vn.haui.dx.core.identity.api.CurrentUser;
import vn.haui.dx.core.identity.api.WorkspaceAccess;
import vn.haui.dx.core.identity.api.WorkspaceRole;

@Component
class WorkspaceAccessImpl implements WorkspaceAccess {
    @Override public CurrentUser current() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken token) return JwtCurrentUser.from(token.getToken());
        throw new CoreException(ErrorCode.UNAUTHENTICATED, "Authentication required");
    }
    @Override public void requireMember(String workspace) {
        if (!current().isMember(workspace)) throw CoreException.forbidden("Workspace access denied");
    }
    @Override public void requireRole(String workspace, WorkspaceRole... anyOf) {
        if (!current().hasRole(workspace, anyOf)) throw CoreException.forbidden("Workspace role required");
    }
}
