import type { ComponentType, ReactNode } from "react";
import { useNavigate } from "react-router";
import { IconArrowLeft } from "@tabler/icons-react";
import { cn } from "cn";

interface Props {
  title: string;
  icon?: ComponentType<{ size?: number; stroke?: number; className?: string }>;
  /** Nút hành động bên phải, ví dụ nút xanh "Tạo ticket". */
  actions?: ReactNode;
  /**
   * Chỉ trang cấp 2 (chi tiết) truyền: hiện nút quay lại. Trang mở từ menu không có nút.
   * Giá trị là trang về khi mở thẳng link (không có trang trước trong lịch sử).
   */
  backTo?: string;
}

/** Hàng tiêu đề trang kiểu Twenty: cao 48px, viền dưới, (nút quay lại) + icon + tiêu đề trái, hành động phải. */
export const PageHeader = ({ title, icon: Icon, actions, backTo }: Props) => {
  const navigate = useNavigate();
  // React Router ghi vị trí trong lịch sử ở history.state.idx; 0 là trang đầu tiên của tab
  const back = (fallback: string) => {
    const index = (window.history.state as { idx?: number } | null)?.idx ?? 0;
    if (index > 0) navigate(-1);
    else navigate(fallback);
  };

  return (
    <header
      className={cn(
        "flex h-12 shrink-0 items-center justify-between gap-2 border-b border-crm-line pr-3",
        backTo === undefined ? "pl-4" : "pl-2",
      )}
    >
      <div className="flex min-w-0 items-center gap-2 text-crm-text">
        {backTo !== undefined && (
          <button
            type="button"
            onClick={() => back(backTo)}
            aria-label="Quay lại"
            className="flex size-8 shrink-0 items-center justify-center rounded-crm-sm text-crm-text-2 transition-colors duration-100 outline-none hover:bg-crm-hover focus-visible:ring-2 focus-visible:ring-crm-focus sm:size-7"
          >
            <IconArrowLeft size={16} stroke={2} />
          </button>
        )}
        {Icon && <Icon size={16} stroke={2} className="shrink-0 text-crm-text-2" />}
        <h1 className="truncate font-medium">{title}</h1>
      </div>
      {actions && <div className="flex shrink-0 items-center gap-2">{actions}</div>}
    </header>
  );
};
