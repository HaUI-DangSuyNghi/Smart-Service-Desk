package vn.haui.dx.core.identity.api;

import java.util.Set;

/**
 * Một thành viên workspace theo Keycloak.
 *
 * @param sub {@code sub} của Keycloak, trùng {@link CurrentUser#sub()} và cột owner/actor ở mọi nơi
 * @param fullName họ tên kiểu Việt: lastName (họ) + firstName (tên), ví dụ "Nguyễn Văn An"; {@code null} nếu chưa khai báo
 */
public record DirectoryMember(String sub, String username, String fullName, Set<WorkspaceRole> roles) {
    public DirectoryMember { roles = Set.copyOf(roles); }
}
