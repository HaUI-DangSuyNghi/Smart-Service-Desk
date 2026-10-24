/**
 * Tên quyền khai báo trong Keycloak (client dx-permissions, xem infra/keycloak/realm-template.json).
 * Dùng hằng số thay vì gõ chuỗi để không sai chính tả.
 */
export const PERMISSIONS = {
  requestCreate: "request:create",
  requestViewAll: "request:view-all",
  taskHandle: "task:handle",
  taskAssign: "task:assign",
  documentView: "document:view",
  documentUpload: "document:upload",
  memberView: "member:view",
  auditView: "audit:view",
  auditViewOwn: "audit:view-own",
  dashboardView: "dashboard:view",
} as const;

export type Permission = (typeof PERMISSIONS)[keyof typeof PERMISSIONS];

/** Có ít nhất một quyền cần thiết; không yêu cầu quyền thì luôn đúng. */
export const hasAnyPermission = (granted: ReadonlySet<string>, required?: readonly string[]) => {
  return !required?.length || required.some((permission) => granted.has(permission));
};
