import {
  IconBell,
  IconBook,
  IconCalendarUser,
  IconChartBar,
  IconHistory,
  IconHome,
  IconHourglass,
  IconInbox,
  IconLayoutSidebarLeftCollapse,
  IconLayoutSidebarLeftExpand,
  IconListNumbers,
  IconSend,
  IconUsers,
} from "@tabler/icons-react";
import { cn } from "cn";
import { hasAnyRole, PERMISSIONS, useActor, usePermissions } from "@/platform/human/workspace";
import { AlertBadge, InboxBadge } from "@/platform/process";
import { isWorkflowAvailable } from "@/platform/services";
import { useIsMobile } from "@/platform/shared/lib/useMediaQuery";
import type { MenuItem } from "../../router/registry";
import { NavItem } from "./NavItem";
import { NavSection } from "./NavSection";
import { useSidebarStore } from "./sidebarStore";
import { WorkspaceSwitcher } from "./WorkspaceSwitcher";

/** Trang của Tầng 2; chỉ liệt kê trang đã có thật. */
const PLATFORM_MENU: readonly MenuItem[] = [
  { label: "Trang chủ", path: "/home", icon: IconHome },
  {
    label: "Dashboard",
    path: "/dashboard",
    icon: IconChartBar,
    tone: "turquoise",
    requiredPermissions: [PERMISSIONS.dashboardView],
  },
  {
    label: "Công việc",
    path: "/inbox",
    icon: IconInbox,
    tone: "red",
    badge: InboxBadge,
    requiredPermissions: [PERMISSIONS.taskHandle],
  },
  {
    label: "Yêu cầu",
    path: "/requests",
    icon: IconSend,
    tone: "green",
    requiredPermissions: [PERMISSIONS.requestCreate],
  },
  {
    label: "Thành viên",
    path: "/members",
    icon: IconUsers,
    tone: "purple",
    requiredPermissions: [PERMISSIONS.memberView],
  },
  {
    label: "Tài liệu",
    path: "/knowledge",
    icon: IconBook,
    tone: "blue",
    requiredPermissions: [PERMISSIONS.documentView],
  },
  {
    label: "Nhật ký",
    path: "/audit",
    icon: IconHistory,
    tone: "gray",
    requiredPermissions: [PERMISSIONS.auditView],
  },
];

/** Hàng đợi, cảnh báo, ca trực, yêu cầu sửa ước tính (plan nghiệp vụ R2–R8); chỉ khi đã sẵn sàng. */
const WORKFLOW_MENU: readonly MenuItem[] = [
  {
    label: "Hàng đợi",
    path: "/queue",
    icon: IconListNumbers,
    tone: "blue",
    requiredPermissions: [PERMISSIONS.taskHandle],
  },
  {
    label: "Cảnh báo",
    path: "/alerts",
    icon: IconBell,
    tone: "orange",
    badge: AlertBadge,
    requiredPermissions: [PERMISSIONS.taskHandle],
  },
  {
    label: "Ca trực",
    path: "/duty",
    icon: IconCalendarUser,
    tone: "turquoise",
    requiredPermissions: [PERMISSIONS.taskAssign],
  },
  {
    label: "Sửa ước tính",
    path: "/estimate-requests",
    icon: IconHourglass,
    tone: "purple",
    requiredPermissions: [PERMISSIONS.taskAssign],
  },
];

const MENU = isWorkflowAvailable ? [...PLATFORM_MENU, ...WORKFLOW_MENU] : PLATFORM_MENU;

interface Props {
  /** Menu do các app (Tầng 3) đăng ký qua registry. */
  appMenu: readonly MenuItem[];
}

/**
 * Sidebar kiểu Twenty: nền xám nhạt, rộng 220px (thu gọn 48px), kẻ dọc ngăn với vùng trang.
 * Dưới md luôn thu gọn và không có nút mở rộng: màn điện thoại không đủ chỗ cho sidebar 220px.
 */
export const AppSidebar = ({ appMenu }: Props) => {
  const isMobile = useIsMobile();
  const collapsedByUser = useSidebarStore((state) => state.collapsed);
  // Không ghi đè lựa chọn của người dùng: quay lại màn rộng thì sidebar về đúng trạng thái cũ
  const collapsed = collapsedByUser || isMobile;
  const toggle = useSidebarStore((state) => state.toggle);
  const ToggleIcon = collapsed ? IconLayoutSidebarLeftExpand : IconLayoutSidebarLeftCollapse;
  // Ẩn mục người dùng không đủ vai trò/quyền ở workspace đang chọn (backend vẫn tự kiểm quyền)
  const { roles } = useActor();
  const { can } = usePermissions();
  const isVisible = (item: MenuItem) =>
    hasAnyRole(roles, item.requiredRoles) && can(...(item.requiredPermissions ?? []));
  const visiblePlatformMenu = MENU.filter(isVisible);
  const visibleAppMenu = appMenu.filter(isVisible);

  return (
    <aside
      className={cn(
        "flex h-full shrink-0 flex-col gap-4 border-r border-crm-line bg-crm-sidebar px-2 pt-3 pb-4",
        "transition-[width] duration-300",
        collapsed ? "w-12 items-center" : "w-55",
      )}
    >
      {/* Hàng đầu: workspace bên trái, nút thu gọn bên phải */}
      <div className={cn("flex items-center gap-1", collapsed ? "flex-col" : "justify-between")}>
        <WorkspaceSwitcher collapsed={collapsed} />
        {!isMobile && (
          <button
            type="button"
            onClick={toggle}
            title={collapsed ? "Mở rộng thanh bên" : "Thu gọn thanh bên"}
            className="grid size-7 shrink-0 place-items-center rounded-crm-sm text-crm-text-3 transition-colors duration-100 hover:bg-crm-hover hover:text-crm-text"
          >
            <ToggleIcon size={16} stroke={2} />
          </button>
        )}
      </div>

      <nav className="flex min-h-0 flex-1 flex-col gap-4 overflow-y-auto">
        <NavSection title="Nền tảng" collapsed={collapsed}>
          {visiblePlatformMenu.map((item) => (
            <NavItem key={item.path} item={item} collapsed={collapsed} />
          ))}
        </NavSection>

        {visibleAppMenu.length > 0 && (
          <NavSection title="Ứng dụng" collapsed={collapsed}>
            {visibleAppMenu.map((item) => (
              <NavItem key={item.path} item={item} collapsed={collapsed} />
            ))}
          </NavSection>
        )}
      </nav>
    </aside>
  );
};
