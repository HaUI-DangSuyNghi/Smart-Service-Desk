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
import { visibleMenu } from "../sidebar/menuTree";
import { HOME_COLORS } from "./homeColors";

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
  ).flatMap((item) => item.children?.map((child) => ({ tone: item.tone, ...child })) ?? [item]);
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
      <h2 className="mb-3 text-crm-sm font-semibold text-crm-text">Truy cập nhanh</h2>
      <div className="grid grid-cols-1 gap-3 sm:grid-cols-2 xl:grid-cols-4">
        {items.map(({ path, label, icon: Icon = IconLayoutGrid, tone = "gray" }) => (
          <Link
            key={path}
            to={path}
            className={cn(
              "group flex min-w-0 items-center gap-3 rounded-2xl border bg-linear-to-br to-crm-surface px-4 py-4 text-crm-text outline-none transition-colors duration-150 hover:border-crm-accent/40 focus-visible:ring-2 focus-visible:ring-crm-focus",
              HOME_COLORS[tone].header,
              HOME_COLORS[tone].border,
            )}
          >
            <span
              className={cn(
                "flex size-11 shrink-0 items-center justify-center rounded-xl",
                HOME_COLORS[tone].icon,
              )}
            >
              <Icon size={22} />
            </span>
            <span className="min-w-0 flex-1 truncate font-medium">{label}</span>
            <IconArrowUpRight
              size={17}
              className="shrink-0 text-crm-text-3 transition-colors group-hover:text-crm-accent"
            />
          </Link>
        ))}
      </div>
    </nav>
  );
};
