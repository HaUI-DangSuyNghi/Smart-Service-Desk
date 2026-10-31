import { useRef, useState, type DragEvent } from "react";
import { IconCloudUpload, IconFileText, IconX } from "@tabler/icons-react";
import { cn } from "cn";
import { formatFileSize } from "@/platform/shared/lib/format";

interface Props {
  id?: string;
  value: File | null;
  onChange: (file: File | null) => void;
  /** Giống thuộc tính accept của input file, ví dụ ".txt,.md". Kéo thả không bị lọc, cần tự kiểm. */
  accept?: string;
  /** Dòng gợi ý dưới tiêu đề, ví dụ "Chỉ .txt hoặc .md, tối đa 5 MB". */
  hint?: string;
  invalid?: boolean;
  disabled?: boolean;
  /** Dạng hàng gọn cho form trong dialog. */
  compact?: boolean;
}

/**
 * Ô chọn file kiểu Upload.Dragger của Ant Design: bấm để mở hộp chọn file hoặc kéo thả vào,
 * file đã chọn hiện ở dòng bên dưới kèm dung lượng và nút bỏ chọn. Mỗi lần một file.
 */
export const FileDropzone = ({
  id,
  value,
  onChange,
  accept,
  hint,
  invalid,
  disabled,
  compact = false,
}: Props) => {
  const inputRef = useRef<HTMLInputElement>(null);
  const [dragging, setDragging] = useState(false);

  const choose = (file: File | null) => {
    onChange(file);
    // Xóa giá trị của input để chọn lại đúng file cũ vẫn kích hoạt onChange
    if (inputRef.current) inputRef.current.value = "";
  };

  const handleDragOver = (event: DragEvent<HTMLLabelElement>) => {
    event.preventDefault(); // cho phép thả
    if (!disabled) setDragging(true);
  };

  const handleDragLeave = (event: DragEvent<HTMLLabelElement>) => {
    // Rời từ ô sang phần tử con bên trong không tính là rời ô
    if (!event.currentTarget.contains(event.relatedTarget as Node | null)) setDragging(false);
  };

  const handleDrop = (event: DragEvent<HTMLLabelElement>) => {
    event.preventDefault();
    setDragging(false);
    if (disabled) return;
    choose(event.dataTransfer.files[0] ?? null);
  };

  return (
    <div className="flex flex-col gap-2">
      <label
        onDragOver={handleDragOver}
        onDragLeave={handleDragLeave}
        onDrop={handleDrop}
        className={cn(
          "flex cursor-pointer items-center rounded-crm-md border border-dashed px-3",
          compact ? "flex-row gap-3 py-2.5 text-left" : "flex-col gap-1 py-6 text-center",
          "transition-colors duration-150 focus-within:border-crm-accent",
          dragging
            ? "border-crm-accent bg-crm-accent-soft"
            : "border-crm-line-strong bg-crm-subtle hover:border-crm-accent",
          invalid && !dragging && "border-crm-danger",
          disabled && "pointer-events-none opacity-60",
        )}
      >
        <input
          ref={inputRef}
          id={id}
          type="file"
          accept={accept}
          disabled={disabled}
          aria-invalid={invalid}
          onChange={(event) => choose(event.target.files?.[0] ?? null)}
          className="sr-only"
        />
        <IconCloudUpload
          size={compact ? 24 : 32}
          stroke={1.6}
          className="shrink-0 text-crm-accent"
        />
        <span className={cn("flex min-w-0 flex-col gap-1", !compact && "items-center")}>
          <span className="text-crm-text">
            {dragging ? "Thả file vào đây" : "Bấm hoặc kéo thả file vào đây"}
          </span>
          {hint && (
            <span className={cn("text-crm-text-3", compact ? "text-crm-xs" : "text-crm-sm")}>
              {hint}
            </span>
          )}
        </span>
      </label>

      {value && (
        <div className="flex h-9 items-center gap-2 rounded-crm-sm px-2 transition-colors duration-100 hover:bg-crm-hover md:h-8">
          <IconFileText size={16} stroke={2} className="shrink-0 text-crm-text-3" />
          <span className="min-w-0 flex-1 truncate text-crm-text">{value.name}</span>
          <span className="shrink-0 text-crm-sm text-crm-text-3">{formatFileSize(value.size)}</span>
          <button
            type="button"
            onClick={() => choose(null)}
            disabled={disabled}
            aria-label="Bỏ chọn file"
            className="grid size-9 shrink-0 place-items-center rounded-crm-sm text-crm-text-3 hover:bg-crm-hover hover:text-crm-text md:size-6"
          >
            <IconX size={14} stroke={2} />
          </button>
        </div>
      )}
    </div>
  );
};
