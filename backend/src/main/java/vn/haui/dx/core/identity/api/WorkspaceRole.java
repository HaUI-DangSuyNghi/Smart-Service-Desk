package vn.haui.dx.core.identity.api;

import java.util.regex.Pattern;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Vai trò trong một workspace, lấy từ group Keycloak {@code /workspaces/{workspace}/{role}}.
 * Ba hằng số là mặc định; thêm role mới chỉ cần tạo group trong Keycloak, không sửa Core.
 */
public record WorkspaceRole(String value) {
    public static final Pattern NAME = Pattern.compile("[a-z0-9-]{1,30}");

    public static final WorkspaceRole REQUESTER = new WorkspaceRole("requester");
    public static final WorkspaceRole AGENT = new WorkspaceRole("agent");
    public static final WorkspaceRole COORDINATOR = new WorkspaceRole("coordinator");

    public WorkspaceRole {
        if (value == null || !NAME.matcher(value).matches()) throw new IllegalArgumentException("Invalid role: " + value);
    }
    /** JSON ghi role dạng chuỗi, ví dụ {@code "agent"}. */
    @JsonValue public String value() { return value; }
    @Override public String toString() { return value; }
}
