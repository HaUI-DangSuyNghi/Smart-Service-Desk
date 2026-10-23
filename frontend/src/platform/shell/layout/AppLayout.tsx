import { Suspense } from "react";
import { Outlet } from "react-router";
import { Loading } from "@/platform/shared/ui/Loading";
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
        <Suspense fallback={<Loading />}>
          <Outlet />
        </Suspense>
      </div>
    </div>
  );
};

export default AppLayout;
