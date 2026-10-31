import type { MenuItem, MenuTone } from "../../router/registry";

export const TONE_CLASS: Record<MenuTone, string> = {
  gray: "text-crm-tone-gray",
  blue: "text-crm-tone-blue",
  red: "text-crm-tone-red",
  green: "text-crm-tone-green",
  orange: "text-crm-tone-orange",
  purple: "text-crm-tone-purple",
  turquoise: "text-crm-tone-turquoise",
};

/**
 * Giữ mục người dùng được thấy. Nhóm chỉ giữ mục con được thấy; không còn con nào thì ẩn cả nhóm
 * (quyền của nhóm tính theo quyền của từng trang con).
 */
export const visibleMenu = (
  items: readonly MenuItem[],
  isVisible: (item: MenuItem) => boolean,
): MenuItem[] =>
  items.flatMap((item) => {
    if (!isVisible(item)) return [];
    if (!item.children) return [item];
    const children = item.children.filter(isVisible);
    return children.length > 0 ? [{ ...item, children }] : [];
  });

/** Trang đang mở thuộc mục này: đúng đường dẫn hoặc trang con (ticket/123), như NavLink. */
export const isPathActive = (pathname: string, path: string) =>
  pathname === path || pathname.startsWith(path.endsWith("/") ? path : `${path}/`);

/**
 * Vị trí mục con đang chọn; -1 khi không có. Nhiều mục khớp (đường dẫn lồng nhau) thì lấy mục
 * khớp dài nhất.
 */
export const activeChildIndex = (children: readonly MenuItem[], pathname: string) => {
  let best = -1;
  children.forEach((child, index) => {
    if (isPathActive(pathname, child.path) && (best < 0 || child.path.length > children[best].path.length))
      best = index;
  });
  return best;
};

/**
 * Nét vẽ bên trái mục con kiểu Twenty: mục cuối không kéo đường dọc xuống tiếp; đoạn từ nhóm
 * xuống mục đang chọn tô đậm.
 */
export type SubItemState =
  | "last-selected"
  | "last-not-selected"
  | "intermediate-selected"
  | "intermediate-before-selected"
  | "intermediate-after-selected";

export const subItemState = (index: number, length: number, selectedIndex: number): SubItemState => {
  const isLast = index === length - 1;
  if (index === selectedIndex) return isLast ? "last-selected" : "intermediate-selected";
  if (isLast) return "last-not-selected";
  return selectedIndex > index ? "intermediate-before-selected" : "intermediate-after-selected";
};
