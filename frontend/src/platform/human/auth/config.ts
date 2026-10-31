import type { UserManagerSettings } from "oidc-client-ts";

export const CALLBACK_PATH = "/auth/callback";
export const SILENT_PATH = "/auth/silent";
export const DEFAULT_RETURN_TO = "/home";

export interface AuthEnv {
  authority: string;
  clientId: string;
}

/** Đọc cấu hình OIDC từ biến VITE_*; thiếu thì báo lỗi rõ ràng ngay khi khởi động. */
export const readAuthEnv = (env: Partial<Record<string, string>>): AuthEnv => {
  const authority = env.VITE_OIDC_AUTHORITY;
  const clientId = env.VITE_OIDC_CLIENT_ID;
  if (!authority || !clientId) {
    throw new Error(
      "Thiếu VITE_OIDC_AUTHORITY hoặc VITE_OIDC_CLIENT_ID. Copy frontend/.env.example thành .env",
    );
  }
  return { authority, clientId };
};

/** Cấu hình UserManager (chưa gồm nơi lưu, xem userManager.ts). */
export const createAuthSettings = (env: AuthEnv, origin: string): UserManagerSettings => {
  return {
    authority: env.authority,
    client_id: env.clientId,
    response_type: "code", // Authorization Code; oidc-client-ts tự thêm PKCE S256
    scope: "openid",
    redirect_uri: origin + CALLBACK_PATH,
    silent_redirect_uri: origin + SILENT_PATH,
    // Đăng xuất xong về trang trong app (cần đăng nhập) để RequireAuth đưa thẳng sang trang đăng nhập,
    // không quay ra landing
    post_logout_redirect_uri: origin + DEFAULT_RETURN_TO,
    automaticSilentRenew: true,
    silentRequestTimeoutInSeconds: 5,
    monitorSession: false,
    loadUserInfo: false,
  };
};

/**
 * Trang quản trị của realm: "http://host/realms/dx-lab" → "http://host/admin/dx-lab/console/".
 * Đăng nhập bằng chính phiên SSO hiện tại; tài khoản cần role realm-admin của realm.
 */
export const adminConsoleUrl = (authority: string) =>
  authority.replace(/\/realms\/([^/]+)\/?$/, "/admin/$1/console/");

/** Chỉ cho quay về đường dẫn nội bộ, chặn open redirect qua state đăng nhập. */
export const safeReturnTo = (value: unknown): string => {
  if (typeof value !== "string" || !value.startsWith("/") || value.startsWith("//")) {
    return DEFAULT_RETURN_TO;
  }
  if (value.startsWith(CALLBACK_PATH) || value.startsWith(SILENT_PATH)) return DEFAULT_RETURN_TO;
  return value;
};
