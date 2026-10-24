package vn.haui.dx.hpdi.human.api;

/**
 * Một quyền người dùng có trong workspace.
 *
 * @param key tên quyền, ví dụ {@code request:create}
 * @param label mô tả tiếng Việt khai báo trong Keycloak; không có thì bằng key
 */
public record WorkspacePermission(String key, String label) {}
