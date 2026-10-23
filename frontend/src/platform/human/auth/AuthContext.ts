import { createContext } from "react";
import type { User } from "oidc-client-ts";

export type AuthStatus = "loading" | "authenticated" | "anonymous" | "signingOut";

export interface AuthContextValue {
  status: AuthStatus;
  user: User | null;
  /** Chuyển sang Keycloak; đăng nhập xong quay về returnTo. */
  login: (returnTo?: string) => Promise<void>;
  logout: () => Promise<void>;
  /** Dùng ở trang callback: đổi code lấy token, trả về đường dẫn cần quay lại. */
  completeLogin: () => Promise<string>;
}

export const AuthContext = createContext<AuthContextValue | null>(null);
