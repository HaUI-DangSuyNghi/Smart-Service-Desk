package vn.haui.dx.core.identity.api;

import java.util.Locale;
import java.util.Optional;

/** Vai trò trong một workspace, lấy từ group Keycloak {@code /workspaces/{workspace}/{role}}. */
public enum WorkspaceRole {
    REQUESTER, AGENT, COORDINATOR;

    /** Tên chữ thường, dùng trong group Keycloak và candidate group BPMN. */
    public String value() { return name().toLowerCase(Locale.ROOT); }

    public static Optional<WorkspaceRole> fromValue(String value) {
        for (WorkspaceRole role : values()) if (role.value().equals(value)) return Optional.of(role);
        return Optional.empty();
    }
}
