import { useEffect } from "react";
import { Outlet, useLocation } from "react-router";
import { LOADING_MIN_MS } from "@/platform/shared/lib/minDuration";
import { Loading } from "@/platform/shared/ui/Loading";
import type { AuthStatus } from "./AuthContext";
import { useAuth } from "./useAuth";

const LOADING_LABELS: Record<AuthStatus, string> = {
  loading: "Đang kiểm tra đăng nhập",
  signingOut: "Đang đăng xuất",
  anonymous: "Đang chuyển tới trang đăng nhập",
  authenticated: "Đang tải",
};

/** Bọc các route cần đăng nhập; chưa đăng nhập thì chuyển sang Keycloak rồi quay lại đúng trang. */
const RequireAuth = () => {
  const { status, login } = useAuth();
  const { pathname, search } = useLocation();
  const isAuthenticated = status === "authenticated";
  // Đang đăng xuất vẫn giữ trang hiện tại dưới lớp phủ, không để trắng trang
  const showContent = isAuthenticated || status === "signingOut";

  useEffect(() => {
    if (status !== "anonymous") return;
    // Chuyển trang sang Keycloak làm mất lớp phủ ngay, nên cho loading hiện đủ thời gian rồi mới chuyển
    const timer = setTimeout(() => void login(pathname + search), LOADING_MIN_MS);
    return () => clearTimeout(timer);
  }, [status, login, pathname, search]);

  // Loading luôn nằm đây để giữ đủ thời gian tối thiểu, nội dung hiện dần bên dưới lớp phủ
  return (
    <div className="relative min-h-svh">
      {showContent && <Outlet />}
      <Loading active={!isAuthenticated} label={LOADING_LABELS[status]} />
    </div>
  );
};

export default RequireAuth;
