import {
  IconBook,
  IconCalendarTime,
  IconCalendarUser,
  IconChartBar,
  IconHistory,
  IconHome,
  IconHourglass,
  IconInbox,
  IconMessageCircle,
  IconLayoutSidebarLeftCollapse,
  IconLayoutSidebarLeftExpand,
  IconSend,
} from "@tabler/icons-react";
import { cn } from "cn";
import { hasAnyRole, PERMISSIONS, useActor, usePermissions } from "@/platform/human/workspace";
import { NotificationBell } from "@/platform/human/notifications";
import { InboxBadge } from "@/platform/process";
import { useIsMobile } from "@/platform/shared/lib/useMediaQuery";
import type { MenuItem } from "../../router/registry";
import { visibleMenu } from "./menuTree";
import { NavGroup } from "./NavGroup";
import { NavItem } from "./NavItem";
import { NavSection } from "./NavSection";
import { useSidebarStore } from "./sidebarStore";
import { WorkspaceSwitcher } from "./WorkspaceSwitcher";

/**
 * Trang của Tầng 2; chỉ liệt kê trang đã có thật. Trang cùng việc gom thành nhóm xổ ra; nhóm
 * hiện khi người dùng thấy được ít nhất một trang con.
 */
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
    label: "Yêu cầu",
    path: "/requests",
    icon: IconSend,
    tone: "green",
    requiredPermissions: [PERMISSIONS.requestCreate],
  },
  // Hộp việc chung của mọi quy trình (duyệt đặt phòng, duyệt ticket...); việc riêng xem ở từng app
  {
    label: "Công việc",
    path: "/inbox",
    icon: IconInbox,
    tone: "red",
    badge: InboxBadge,
    requiredPermissions: [PERMISSIONS.taskHandle],
  },
  // Cảnh báo nằm trong chuông thông báo ở đầu sidebar (trang /alerts vẫn mở được)
  // Ca trực (R3), sửa ước tính (R2); nhóm hiện khi người dùng thấy ít nhất một mục con
  {
    label: "Điều phối",
    path: "/coordination",
    icon: IconCalendarUser,
    tone: "turquoise",
    children: [
      // Kỹ thuật viên đăng ký ca của mình; điều phối viên xem lịch cả workspace, đăng ký hộ, duyệt
      {
        label: "Ca trực",
        path: "/duty",
        icon: IconCalendarTime,
        requiredPermissions: [PERMISSIONS.taskHandle, PERMISSIONS.taskAssign],
      },
      {
        label: "Sửa ước tính",
        path: "/estimate-requests",
        icon: IconHourglass,
        requiredPermissions: [PERMISSIONS.taskAssign],
      },
    ],
  },
  {
    label: "Tài liệu",
    path: "/knowledge",
    icon: IconBook,
    tone: "blue",
    requiredPermissions: [PERMISSIONS.documentView],
  },
  {
    label: "Hỏi đáp tài liệu",
    path: "/knowledge-chat",
    icon: IconMessageCircle,
    tone: "blue",
    requiredPermissions: [PERMISSIONS.documentView],
  },
  {
    label: "Nhật ký",
    path: "/audit",
    icon: IconHistory,
    tone: "purple",
    requiredPermissions: [PERMISSIONS.auditView],
  },
];

/** Mục menu: nhóm thì xổ ra; sidebar thu gọn (và điện thoại) không có chỗ xổ nên hiện thẳng icon từng trang con. */
const MenuEntries = ({ items, collapsed }: { items: readonly MenuItem[]; collapsed: boolean }) =>
  items.map((item) => {
    if (!item.children) return <NavItem key={item.path} item={item} collapsed={collapsed} />;
    if (!collapsed) return <NavGroup key={item.path} item={item} />;
    return item.children.map((child) => (
      <NavItem key={child.path} item={{ tone: item.tone, ...child }} collapsed />
    ));
  });

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
  const visiblePlatformMenu = visibleMenu(PLATFORM_MENU, isVisible);
  const visibleAppMenu = visibleMenu(appMenu, isVisible);

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
        <NotificationBell collapsed={collapsed} />
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
          <MenuEntries items={visiblePlatformMenu} collapsed={collapsed} />
        </NavSection>

        {visibleAppMenu.length > 0 && (
          <NavSection title="Ứng dụng" collapsed={collapsed}>
            <MenuEntries items={visibleAppMenu} collapsed={collapsed} />
          </NavSection>
        )}
      </nav>
    </aside>
  );
};
