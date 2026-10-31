import { NavLink } from "react-router";
import { cn } from "cn";
import type { MenuItem } from "../../router/registry";
import { TONE_CLASS } from "./menuTree";

interface Props {
  item: MenuItem;
  collapsed: boolean;
}

/** Mục menu kiểu Twenty: cao 28px (36px trên mobile cho dễ chạm), đệm 8px, bo 4px, icon màu. */
export const NavItem = ({ item, collapsed }: Props) => {
  const { label, path, icon: Icon, tone = "gray", badge: Badge } = item;

  return (
    <NavLink
      to={path}
      title={collapsed ? label : undefined}
      // Thu gọn chỉ còn icon nên cần tên cho trình đọc màn hình
      aria-label={collapsed ? label : undefined}
      className={({ isActive }) =>
        cn(
          "relative flex h-9 shrink-0 items-center gap-2 rounded-crm-sm px-2 text-crm-text md:h-7",
          "transition-colors duration-100 hover:bg-crm-hover",
          isActive && "bg-crm-hover",
          collapsed && "w-8 justify-center px-0",
        )
      }
    >
      {Icon && <Icon size={16} stroke={2} className={cn("shrink-0", TONE_CLASS[tone])} />}
      {!collapsed && <span className="min-w-0 flex-1 truncate">{label}</span>}
      {/* Thu gọn thì số đếm nằm đè góc icon */}
      {Badge && (
        <span className={cn("flex", collapsed && "absolute top-0 right-0")}>
          <Badge />
        </span>
      )}
    </NavLink>
  );
};
