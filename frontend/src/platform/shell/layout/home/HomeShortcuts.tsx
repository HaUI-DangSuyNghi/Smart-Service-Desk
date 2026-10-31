import { Link } from "react-router";
import {
  IconArrowUpRight,
  IconCalendarTime,
  IconChartBar,
  IconLayoutGrid,
  IconUser,
} from "@tabler/icons-react";
import { cn } from "cn";
import { hasAnyRole, PERMISSIONS, useActor, usePermissions } from "@/platform/human/workspace";
import type { MenuItem } from "../../router/registry";
import { TONE_CLASS, visibleMenu } from "../sidebar/menuTree";

interface Props {
  appMenu: readonly MenuItem[];
}

export const HomeShortcuts = ({ appMenu }: Props) => {
  const { can } = usePermissions();
  const { roles, error, isPending } = useActor();
  const apps = visibleMenu(
    appMenu,
    (item) =>
      can(...(item.requiredPermissions ?? [])) &&
      (!item.requiredRoles?.length ||
        (!isPending && !error && hasAnyRole(roles, item.requiredRoles))),
  ).flatMap((item) => item.children ?? [item]);
  const items: MenuItem[] = [
    ...apps,
    ...(can(PERMISSIONS.taskHandle, PERMISSIONS.taskAssign)
      ? [{ label: "Lịch trực", path: "/duty", icon: IconCalendarTime, tone: "green" as const }]
      : []),
    ...(can(PERMISSIONS.dashboardView)
      ? [{ label: "Thống kê", path: "/dashboard", icon: IconChartBar, tone: "blue" as const }]
      : []),
  ];
  if (items.length === 0) {
    items.push({ label: "Hồ sơ cá nhân", path: "/profile", icon: IconUser, tone: "gray" });
  }
  return (
    <nav aria-label="Truy cập nhanh">
      <h2 className="mb-3 text-crm-sm font-medium text-crm-text-2">Truy cập nhanh</h2>
      <div className="grid grid-cols-1 gap-2 sm:grid-cols-2 xl:grid-cols-4">
        {items.map(({ path, label, icon: Icon = IconLayoutGrid, tone = "gray" }) => (
          <Link
            key={path}
            to={path}
            className="flex min-w-0 items-center gap-3 rounded-crm-md border border-crm-line px-3 py-3 text-crm-text outline-none transition-colors duration-100 hover:bg-crm-hover focus-visible:ring-2 focus-visible:ring-crm-focus"
          >
            <Icon size={20} className={cn("shrink-0", TONE_CLASS[tone])} />
            <span className="min-w-0 flex-1 truncate font-medium">{label}</span>
            <IconArrowUpRight size={14} className="shrink-0 text-crm-text-3" />
          </Link>
        ))}
      </div>
    </nav>
  );
};
