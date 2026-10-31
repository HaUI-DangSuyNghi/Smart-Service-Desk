import { useState } from "react";
import { IconChevronLeft, IconChevronRight } from "@tabler/icons-react";
import { Button } from "./button";

interface Props {
  /** Trang hiện tại, tính từ 0 như API. */
  page: number;
  total: number;
  pageSize: number;
  onPageChange: (page: number) => void;
  /** Có ô nhập số trang để nhảy thẳng tới trang đó (đặt ở chân bảng). */
  editable?: boolean;
}

interface PageInputProps {
  page: number;
  pageCount: number;
  onPageChange: (page: number) => void;
}

/** Ô nhập số trang (tính từ 1): Enter hoặc rời ô thì nhảy tới; số ngoài khoảng thì kẹp vào 1..pageCount. */
const PageInput = ({ page, pageCount, onPageChange }: PageInputProps) => {
  const [draft, setDraft] = useState(String(page + 1));

  const commit = () => {
    const value = Number.parseInt(draft, 10);
    const next = Number.isNaN(value) ? page : Math.min(Math.max(value, 1), pageCount) - 1;
    setDraft(String(next + 1));
    if (next !== page) onPageChange(next);
  };

  return (
    <input
      value={draft}
      onChange={(event) => setDraft(event.target.value.replace(/\D/g, ""))}
      onBlur={commit}
      onKeyDown={(event) => event.key === "Enter" && event.currentTarget.blur()}
      inputMode="numeric"
      aria-label="Số trang"
      className="h-7 w-12 rounded-crm-sm border border-crm-line bg-transparent text-center text-crm-text tabular-nums outline-none focus:border-crm-focus"
    />
  );
};

/** "1/3 ‹ ›" ở góc phải toolbar, dùng chung cho mọi bảng có phân trang. */
export const Pagination = ({ page, total, pageSize, onPageChange, editable }: Props) => {
  const pageCount = Math.max(1, Math.ceil(total / pageSize));

  return (
    <div className="flex shrink-0 items-center gap-1">
      {editable ? (
        <span className="flex items-center gap-1.5 px-2 text-crm-text-3 tabular-nums">
          {/* key: nhảy trang từ nơi khác (nút ‹ ›) thì ô nhập lấy lại số trang mới */}
          <PageInput
            key={page}
            page={page}
            pageCount={pageCount}
            onPageChange={onPageChange}
          />/ {pageCount}
        </span>
      ) : (
        <span className="px-2 text-crm-text-3 tabular-nums">
          {page + 1}/{pageCount}
        </span>
      )}
      <Button
        variant="ghost"
        size="icon-sm"
        aria-label="Trang trước"
        disabled={page === 0}
        onClick={() => onPageChange(page - 1)}
      >
        <IconChevronLeft size={16} stroke={2} />
      </Button>
      <Button
        variant="ghost"
        size="icon-sm"
        aria-label="Trang sau"
        disabled={page + 1 >= pageCount}
        onClick={() => onPageChange(page + 1)}
      >
        <IconChevronRight size={16} stroke={2} />
      </Button>
    </div>
  );
};
