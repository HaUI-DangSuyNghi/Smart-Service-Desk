import { IconChevronLeft, IconChevronRight } from "@tabler/icons-react";
import { Button } from "./button";

interface Props {
  /** Trang hiện tại, tính từ 0 như API. */
  page: number;
  total: number;
  pageSize: number;
  onPageChange: (page: number) => void;
}

/** "1/3 ‹ ›" ở góc phải toolbar, dùng chung cho mọi bảng có phân trang. */
export const Pagination = ({ page, total, pageSize, onPageChange }: Props) => {
  const pageCount = Math.max(1, Math.ceil(total / pageSize));

  return (
    <div className="flex shrink-0 items-center gap-1">
      <span className="px-2 text-crm-text-3 tabular-nums">
        {page + 1}/{pageCount}
      </span>
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
