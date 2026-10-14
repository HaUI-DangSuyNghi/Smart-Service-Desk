package vn.haui.dx.core.identity.api;

import java.util.Set;

/** Người gọi hiện tại, dựng từ access token đã kiểm chữ ký. */
public interface CurrentUser {
    /** {@code sub} của Keycloak, không đổi; dùng làm actor/owner ở mọi nơi. */
    String sub();
    String username();
    Set<Membership> memberships();
    /** Realm role {@code platform-admin}; không tự có quyền đọc nội dung workspace. */
    boolean platformAdmin();
    boolean isMember(String workspace);
    boolean hasRole(String workspace, WorkspaceRole... anyOf);
}
