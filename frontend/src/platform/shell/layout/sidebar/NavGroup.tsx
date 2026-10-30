import { useState } from "react";
import { NavLink, useLocation, useNavigate } from "react-router";
import { IconChevronRight } from "@tabler/icons-react";
import { cn } from "cn";
import type { MenuItem } from "../../router/registry";
import { activeChildIndex, subItemState, type SubItemState } from "./menuTree";
import { TONE_CLASS } from "./NavItem";

// Nét dẫn hướng: xám nhạt; đoạn từ nhóm xuống mục đang chọn đậm hơn
const lineClass = (darker: boolean) => (darker ? "bg-crm-text-3" : "bg-crm-line-strong");
const curveClass = (darker: boolean) =>
  darker ? "z-1 border-crm-text-3" : "border-crm-line-strong";

/**
 * Đường dọc bên trái mục con (giống Twenty): nối khe 2px với mục trên, góc bo hình chữ L tới giữa
 * hàng, và thân dọc chạy tiếp xuống (mục cuối không có). Căn dưới tâm icon của nhóm.
 */
const Breadcrumb = ({ state }: { state: SubItemState }) => {
  const selected = state === "intermediate-selected" || state === "last-selected";
  const beforeSelected = state === "intermediate-before-selected";
  const isLast = state === "last-selected" || state === "last-not-selected";
  return (
    <span aria-hidden className="relative ms-[7.5px] me-2 h-7 w-2.25 shrink-0">
      <span className={cn("absolute -top-0.5 h-0.5 w-px", lineClass(selected || beforeSelected))} />
      <span
        className={cn(
          "absolute top-0 h-3.5 w-2 rounded-es-crm-sm border-s border-b",
          curveClass(selected),
        )}
      />
      {!isLast && <span className={cn("absolute top-0 h-full w-px", lineClass(beforeSelected))} />}
    </span>
  );
};

const SubItem = ({ item, state }: { item: MenuItem; state: SubItemState }) => {
  const { label, path, icon: Icon, badge: Badge } = item;
  return (
    // Tô theo state (mục khớp dài nhất) thay vì isActive của NavLink, vì hai mục con có thể cùng gốc
    <NavLink
      to={path}
      className={cn(
        "mt-0.5 flex h-7 shrink-0 items-center rounded-crm-sm pe-2 text-crm-text-2",
        "transition-colors duration-100 hover:bg-crm-hover hover:text-crm-text",
        (state === "intermediate-selected" || state === "last-selected") &&
          "bg-crm-hover text-crm-text",
      )}
    >
      <Breadcrumb state={state} />
      {/* Mục con dùng icon xám như Twenty, màu chỉ ở hàng nhóm */}
      {Icon && <Icon size={16} stroke={2} className="me-2 shrink-0 text-crm-text-3" />}
      <span className="min-w-0 flex-1 truncate">{label}</span>
      {Badge && <Badge />}
    </NavLink>
  );
};

interface Props {
  /** Nhóm đã lọc theo quyền: có ít nhất một mục con. */
  item: MenuItem;
}

/**
 * Nhóm menu xổ ra kiểu Twenty (Workflows ▸ All Workflows...). Bấm hàng nhóm để mở/đóng; mở thì vào
 * trang con đầu tiên nếu chưa ở trang nào của nhóm. Đang ở trang con thì tự mở, trừ khi người dùng
 * vừa tự đóng. Không nhớ qua lần mở app sau (như Twenty).
 */
export const NavGroup = ({ item }: Props) => {
  const { label, icon: Icon, tone = "gray" } = item;
  const children = item.children ?? [];
  const { pathname } = useLocation();
  const navigate = useNavigate();
  const selectedIndex = activeChildIndex(children, pathname);
  const hasActiveChild = selectedIndex >= 0;
  // null: chưa bấm, theo trang đang mở hoặc mở sẵn
  const [toggled, setToggled] = useState<boolean | null>(null);
  const isOpen = toggled ?? (hasActiveChild || Boolean(item.defaultOpen));

  const handleToggle = () => {
    setToggled(!isOpen);
    if (!isOpen && !hasActiveChild) void navigate(children[0].path);
  };

  return (
    <div className="flex flex-col">
      <button
        type="button"
        onClick={handleToggle}
        aria-expanded={isOpen}
        className={cn(
          "flex h-9 shrink-0 items-center gap-2 rounded-crm-sm px-2 text-left text-crm-text md:h-7",
          "transition-colors duration-100 hover:bg-crm-hover",
          // Đóng mà đang ở trang con thì tô hàng nhóm để biết mình đang ở đâu
          !isOpen && hasActiveChild && "bg-crm-hover",
        )}
      >
        {Icon && <Icon size={16} stroke={2} className={cn("shrink-0", TONE_CLASS[tone])} />}
        <span className="min-w-0 flex-1 truncate">{label}</span>
        {/* Đóng thì số đếm của trang con hiện trên hàng nhóm để không bị giấu */}
        {!isOpen &&
          children.map(({ path, badge: Badge }) => Badge && <Badge key={path} />)}
        <IconChevronRight
          size={14}
          stroke={2}
          className={cn(
            "shrink-0 text-crm-text-3 transition-transform duration-200 motion-reduce:transition-none",
            isOpen && "rotate-90",
          )}
        />
      </button>
      {/* Cao từ 0 tới đủ nội dung bằng grid-rows, không cần đo chiều cao */}
      <div
        className={cn(
          "grid transition-[grid-template-rows] duration-200 motion-reduce:transition-none",
          isOpen ? "grid-rows-[1fr]" : "grid-rows-[0fr]",
        )}
      >
        <div className="flex min-h-0 flex-col overflow-hidden" inert={!isOpen}>
          {children.map((child, index) => (
            <SubItem
              key={child.path}
              item={child}
              state={subItemState(index, children.length, selectedIndex)}
            />
          ))}
        </div>
      </div>
    </div>
  );
};
