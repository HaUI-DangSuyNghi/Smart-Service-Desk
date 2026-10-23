import { IconHome, IconLayoutSidebarLeftCollapse, IconLayoutSidebarLeftExpand } from "@tabler/icons-react";
import { cn } from "cn";
import type { MenuItem } from "../../router/registry";
import { NavItem } from "./NavItem";
import { NavSection } from "./NavSection";
import { useSidebarStore } from "./sidebarStore";
import { WorkspaceSwitcher } from "./WorkspaceSwitcher";

/** Trang của Tầng 2; chỉ liệt kê trang đã có thật. */
const PLATFORM_MENU: readonly MenuItem[] = [{ label: "Trang chủ", path: "/home", icon: IconHome }];

interface Props {
  /** Menu do các app (Tầng 3) đăng ký qua registry. */
  appMenu: readonly MenuItem[];
}

/** Sidebar kiểu Twenty: nền xám nhạt, rộng 220px (thu gọn 48px), kẻ dọc ngăn với vùng trang. */
export const AppSidebar = ({ appMenu }: Props) => {
  const collapsed = useSidebarStore((state) => state.collapsed);
  const toggle = useSidebarStore((state) => state.toggle);
  const ToggleIcon = collapsed ? IconLayoutSidebarLeftExpand : IconLayoutSidebarLeftCollapse;

  return (
    <aside
      className={cn(
        "flex h-full shrink-0 flex-col gap-4 border-r border-crm-line bg-crm-sidebar px-2 pt-3 pb-4",
        "transition-[width] duration-300",
        collapsed ? "w-12 items-center" : "w-[220px]",
      )}
    >
      {/* Hàng đầu: workspace bên trái, nút thu gọn bên phải */}
      <div className={cn("flex items-center gap-1", collapsed ? "flex-col" : "justify-between")}>
        <WorkspaceSwitcher collapsed={collapsed} />
        <button
          type="button"
          onClick={toggle}
          title={collapsed ? "Mở rộng thanh bên" : "Thu gọn thanh bên"}
          className="grid size-7 shrink-0 place-items-center rounded-crm-sm text-crm-text-3 transition-colors duration-100 hover:bg-crm-hover hover:text-crm-text"
        >
          <ToggleIcon size={16} stroke={2} />
        </button>
      </div>

      <nav className="flex min-h-0 flex-1 flex-col gap-4 overflow-y-auto">
        <NavSection title="Nền tảng" collapsed={collapsed}>
          {PLATFORM_MENU.map((item) => (
            <NavItem key={item.path} item={item} collapsed={collapsed} />
          ))}
        </NavSection>

        {appMenu.length > 0 && (
          <NavSection title="Ứng dụng" collapsed={collapsed}>
            {appMenu.map((item) => (
              <NavItem key={item.path} item={item} collapsed={collapsed} />
            ))}
          </NavSection>
        )}
      </nav>
    </aside>
  );
};
