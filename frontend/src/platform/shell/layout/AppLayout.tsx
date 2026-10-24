import { Suspense } from "react";
import { Outlet } from "react-router";
import { PageSkeleton } from "@/platform/shared/ui/Skeleton";
import { Toaster } from "@/platform/shared/ui/Toaster";
import type { MenuItem } from "../router/registry";
import { AppSidebar } from "./sidebar/AppSidebar";

interface Props {
  menu: readonly MenuItem[];
}

/** Khung app sau đăng nhập kiểu Twenty: sidebar xám bên trái, vùng trang trắng bên phải. */
const AppLayout = ({ menu }: Props) => {
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
    </div>
  );
};

export default AppLayout;
