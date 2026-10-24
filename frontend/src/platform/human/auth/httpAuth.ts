import type { HttpAuth } from "@/platform/shared/api";
import { currentAccessToken, getUserManager, renewAccessToken } from "./userManager";

/** Nối http client (shared/api) với auth. App.tsx gọi configureHttpAuth(httpAuth) lúc khởi động. */
export const httpAuth: HttpAuth = {
  getAccessToken: currentAccessToken,
  renewAccessToken,
  onUnauthorized: () => {
    const returnTo = window.location.pathname + window.location.search;
    void getUserManager().signinRedirect({ state: { returnTo } });
  },
};
