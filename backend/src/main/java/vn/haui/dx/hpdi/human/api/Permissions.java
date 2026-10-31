package vn.haui.dx.hpdi.human.api;

/**
 * Tên quyền khai báo trong Keycloak (role của client {@code dx-permissions}, xem infra/keycloak/realm-template.json).
 * Gom một chỗ để controller không gõ chuỗi rời rạc.
 */
public final class Permissions {
    public static final String REQUEST_CREATE = "request:create";
    public static final String REQUEST_VIEW_ALL = "request:view-all";
    public static final String TASK_HANDLE = "task:handle";
    public static final String TASK_ASSIGN = "task:assign";
    public static final String DOCUMENT_VIEW = "document:view";
    public static final String DOCUMENT_UPLOAD = "document:upload";
    public static final String MEMBER_VIEW = "member:view";
    public static final String AUDIT_VIEW = "audit:view";
    public static final String AUDIT_VIEW_OWN = "audit:view-own";
    public static final String DASHBOARD_VIEW = "dashboard:view";

    private Permissions() {}
}
