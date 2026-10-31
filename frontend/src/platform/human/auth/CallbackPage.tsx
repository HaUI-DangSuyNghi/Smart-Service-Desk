import { useEffect, useRef, useState } from "react";
import { useNavigate } from "react-router";
import { Button } from "@/platform/shared/ui/button";
import { FullPageMessage } from "@/platform/shared/ui/FullPageMessage";
import { withMinDuration } from "@/platform/shared/lib/minDuration";
import { Loading } from "@/platform/shared/ui/Loading";
import { useAuth } from "./useAuth";

/** Keycloak chuyển về đây kèm ?code&state sau khi đăng nhập. */
const CallbackPage = () => {
  const { completeLogin, login } = useAuth();
  const navigate = useNavigate();
  const [failed, setFailed] = useState(false);
  const started = useRef(false);

  useEffect(() => {
    // StrictMode chạy effect 2 lần, nhưng mỗi code chỉ đổi lấy token được 1 lần
    if (started.current) return;
    started.current = true;

    withMinDuration(completeLogin())
      .then((returnTo) => navigate(returnTo, { replace: true }))
      .catch(() => setFailed(true));
  }, [completeLogin, navigate]);

  if (failed) {
    return (
      <FullPageMessage title="Đăng nhập không thành công. Phiên đăng nhập có thể đã hết hạn.">
        <Button onClick={() => void login()}>Đăng nhập lại</Button>
      </FullPageMessage>
    );
  }
  return (
    <div className="relative min-h-svh">
      <Loading label="Đang đăng nhập" />
    </div>
  );
};

export default CallbackPage;
