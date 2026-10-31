import { Suspense } from "react";
import { Outlet } from "react-router";
import { useQueryClient } from "@tanstack/react-query";
import { httpAuth } from "@/platform/human/auth";
import { NotificationWatcher } from "@/platform/human/notifications";
import { useProcessRealtime } from "@/platform/process";
import { useRealtimeConnection } from "@/platform/shared/realtime";
import { PageSkeleton } from "@/platform/shared/ui/Skeleton";
import { Toaster } from "@/platform/shared/ui/Toaster";
import type { MenuItem } from "../router/registry";
import { AppSidebar } from "./sidebar/AppSidebar";

interface Props {
  menu: readonly MenuItem[];
}

/** Khung app sau đăng nhập kiểu Twenty: sidebar xám bên trái, vùng trang trắng bên phải. */
const AppLayout = ({ menu }: Props) => {
  const queryClient = useQueryClient();
  // Một kết nối WebSocket cho cả app; nối lại sau khi rớt thì làm mới mọi dữ liệu đang hiện (có thể đã lỡ sự kiện)
  useRealtimeConnection({
    getAccessToken: httpAuth.getAccessToken,
    onReconnect: () => void queryClient.invalidateQueries(),
  });
  useProcessRealtime();
  return (
    <div className="crm-app flex h-svh overflow-hidden">
      <AppSidebar appMenu={menu} />
      <div className="relative flex min-w-0 flex-1 flex-col">
        {/* Trong khung app, trang đang tải hiện skeleton thay cho Loading toàn màn */}
        <Suspense fallback={<PageSkeleton />}>
          <Outlet />
        </Suspense>
      </div>
      <Toaster />
      {/* Thông báo mới (đổi trạng thái, cảnh báo quá hạn, khẩn cấp...) hiện toast ở mọi trang */}
      <NotificationWatcher />
    </div>
  );
};

export default AppLayout;
