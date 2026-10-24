package vn.haui.dx.core.identity.api;

/**
 * Một quyền nhỏ khai báo trong Keycloak (role của client {@code dx-permissions}).
 *
 * @param name tên dùng trong code, dạng {@code đối-tượng:hành-động}, ví dụ {@code request:create}
 * @param description mô tả tiếng Việt để hiển thị; không có thì null
 */
public record DirectoryPermission(String name, String description) {}
