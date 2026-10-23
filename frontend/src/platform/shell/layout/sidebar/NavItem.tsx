import { NavLink } from "react-router";
import { cn } from "cn";
import type { MenuItem, MenuTone } from "../../router/registry";

// Viết đủ tên class để Tailwind sinh ra được
const TONE_CLASS: Record<MenuTone, string> = {
  gray: "text-crm-tone-gray",
  blue: "text-crm-tone-blue",
  red: "text-crm-tone-red",
  green: "text-crm-tone-green",
  orange: "text-crm-tone-orange",
  purple: "text-crm-tone-purple",
  turquoise: "text-crm-tone-turquoise",
};

interface Props {
  item: MenuItem;
  collapsed: boolean;
}

/** Mục menu kiểu Twenty: cao 28px, đệm 8px, bo 4px, icon màu; đang chọn/hover phủ lớp mờ. */
export const NavItem = ({ item, collapsed }: Props) => {
  const { label, path, icon: Icon, tone = "gray" } = item;

  return (
    <NavLink
      to={path}
      title={collapsed ? label : undefined}
      className={({ isActive }) =>
        cn(
          "flex h-7 shrink-0 items-center gap-2 rounded-crm-sm px-2 text-crm-text",
          "transition-colors duration-100 hover:bg-crm-hover",
          isActive && "bg-crm-hover",
          collapsed && "w-8 justify-center px-0",
        )
      }
    >
      {Icon && <Icon size={16} stroke={2} className={cn("shrink-0", TONE_CLASS[tone])} />}
      {!collapsed && <span className="truncate">{label}</span>}
    </NavLink>
  );
};
