import { adminConsoleUrl, readAuthEnv } from "./config";

/** Link trang quản trị Keycloak của realm đang đăng nhập (quản lý người dùng, nhóm, vai trò). */
export const keycloakAdminUrl = (): string =>
  adminConsoleUrl(readAuthEnv(import.meta.env).authority);
