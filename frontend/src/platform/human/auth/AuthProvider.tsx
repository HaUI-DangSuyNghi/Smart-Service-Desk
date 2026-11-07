import { useCallback, useEffect, useMemo, useRef, useState, type ReactNode } from "react";
import type { User } from "oidc-client-ts";
import { useQueryClient } from "@tanstack/react-query";
import { invalidateHttpSession } from "@/platform/shared/api";
import { LOADING_MIN_MS, wait } from "@/platform/shared/lib/minDuration";
import { AuthContext, type AuthContextValue, type AuthStatus } from "./AuthContext";
import { CALLBACK_PATH, DEFAULT_RETURN_TO, safeReturnTo } from "./config";
import { getUserManager, restoreSession } from "./userManager";

interface Props {
  children: ReactNode;
}

export const AuthProvider = ({ children }: Props) => {
  const queryClient = useQueryClient();
  const subject = useRef<string | null>(null);
  const sessionRevision = useRef(0);
  const [user, setUser] = useState<User | null>(null);
  const [status, setStatus] = useState<AuthStatus>("loading");
  const signingOut = useRef(false);

  useEffect(() => {
    let active = true;
    const handleLoaded = (loaded: User) => {
      if (!active || signingOut.current) return;
      sessionRevision.current++;
      if (subject.current !== loaded.profile.sub) {
        invalidateHttpSession();
        queryClient.clear();
      }
      subject.current = loaded.profile.sub;
      setUser(loaded);
      setStatus("authenticated");
    };
    const handleSignedOut = () => {
      // Đang đăng xuất: thư viện xóa user trước khi chuyển sang Keycloak.
      // Bỏ qua để RequireAuth không gọi đăng nhập lại chen ngang.
      if (!active || signingOut.current) return;
      sessionRevision.current++;
      subject.current = null;
      invalidateHttpSession();
      queryClient.clear();
      setUser(null);
      setStatus("anonymous");
    };

    const { events } = getUserManager();
    events.addUserLoaded(handleLoaded);
    events.addUserUnloaded(handleSignedOut);
    events.addAccessTokenExpired(handleSignedOut);
    events.addSilentRenewError(handleSignedOut);

    // Trang callback tự hoàn tất đăng nhập; sự kiện userLoaded sẽ cập nhật trạng thái
    if (window.location.pathname !== CALLBACK_PATH) {
      const revision = sessionRevision.current;
      void restoreSession().then(
        (restored) => {
          if (revision !== sessionRevision.current) return;
          if (restored) handleLoaded(restored);
          else handleSignedOut();
        },
        () => {
          if (revision === sessionRevision.current) handleSignedOut();
        },
      );
    }

    return () => {
      active = false;
      events.removeUserLoaded(handleLoaded);
      events.removeUserUnloaded(handleSignedOut);
      events.removeAccessTokenExpired(handleSignedOut);
      events.removeSilentRenewError(handleSignedOut);
    };
  }, [queryClient]);

  const login = useCallback(
    (returnTo = DEFAULT_RETURN_TO) => getUserManager().signinRedirect({ state: { returnTo } }),
    [],
  );

  const logout = useCallback(async () => {
    signingOut.current = true;
    sessionRevision.current++;
    subject.current = null;
    invalidateHttpSession();
    queryClient.clear();
    setStatus("signingOut");
    // Cho lớp phủ "đang đăng xuất" hiện đủ thời gian trước khi rời trang sang Keycloak
    await wait(LOADING_MIN_MS);
    try {
      await getUserManager().signoutRedirect();
    } catch {
      // Không tới được Keycloak: vẫn coi là đã đăng xuất trên trình duyệt này
      signingOut.current = false;
      setUser(null);
      setStatus("anonymous");
    }
  }, [queryClient]);

  const completeLogin = useCallback(async () => {
    const signedIn = await getUserManager().signinRedirectCallback();
    return safeReturnTo((signedIn.state as { returnTo?: unknown } | undefined)?.returnTo);
  }, []);

  const value = useMemo<AuthContextValue>(
    () => ({ status, user, login, logout, completeLogin }),
    [status, user, login, logout, completeLogin],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
};
