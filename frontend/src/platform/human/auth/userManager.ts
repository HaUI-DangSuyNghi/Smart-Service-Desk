import { InMemoryWebStorage, UserManager, WebStorageStateStore, type User } from "oidc-client-ts";
import { createAuthSettings, readAuthEnv } from "./config";

let instance: UserManager | null = null;

/** UserManager duy nhất của app, tạo ở lần gọi đầu (import file không có side effect). */
export const getUserManager = (): UserManager => {
  instance ??= new UserManager({
    ...createAuthSettings(readAuthEnv(import.meta.env), window.location.origin),
    // Token chỉ nằm trong bộ nhớ: F5 là mất, lấy lại bằng đăng nhập ngầm (restoreSession)
    userStore: new WebStorageStateStore({ store: new InMemoryWebStorage() }),
    // state + code_verifier của PKCE phải sống qua lần chuyển trang sang Keycloak
    stateStore: new WebStorageStateStore({ store: window.sessionStorage }),
  });
  return instance;
};

/** Khôi phục phiên khi mở trang/F5: dựa vào phiên SSO của Keycloak, không hỏi lại mật khẩu. */
export const restoreSession = async (): Promise<User | null> => {
  const user = await getUserManager().getUser();
  if (user && !user.expired) return user;

  try {
    return await getUserManager().signinSilent();
  } catch {
    return null; // Phiên Keycloak đã hết: coi như chưa đăng nhập
  }
};

/** Token hiện tại hoặc null. */
export const currentAccessToken = async (): Promise<string | null> => {
  const user = await getUserManager().getUser();
  return user && !user.expired ? user.access_token : null;
};

/** Gia hạn token ngay; null nếu không được. */
export const renewAccessToken = async (): Promise<string | null> => {
  try {
    const user = await getUserManager().signinSilent();
    return user?.access_token ?? null;
  } catch {
    return null;
  }
};
