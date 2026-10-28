import { cn } from "cn";
import { Tooltip } from "./Tooltip";

// Viết đủ tên class để Tailwind sinh ra được
const COLORS = [
  "bg-crm-tone-blue-soft text-crm-tone-blue",
  "bg-crm-tone-green-soft text-crm-tone-green",
  "bg-crm-tone-orange-soft text-crm-tone-orange",
  "bg-crm-tone-purple-soft text-crm-tone-purple",
  "bg-crm-tone-turquoise-soft text-crm-tone-turquoise",
  "bg-crm-tone-red-soft text-crm-tone-red",
] as const;

/** Chữ cái đầu của từ đầu và từ cuối (tên Việt: họ + tên); không có tên thì "?". */
export const initialsOf = (name: string) => {
  const words = name.trim().split(/\s+/).filter(Boolean);
  if (words.length === 0) return "?";
  const first = words[0]!;
  const last = words[words.length - 1]!;
  return (
    words.length === 1 ? first.slice(0, 1) : first.slice(0, 1) + last.slice(0, 1)
  ).toUpperCase();
};

const colorOf = (name: string) => {
  let hash = 0;
  for (const char of name) hash = (hash * 31 + char.codePointAt(0)!) >>> 0;
  return COLORS[hash % COLORS.length]!;
};

interface Props {
  name: string;
  className?: string;
  /** Có thì avatar là nút bấm được (không lan lên phần tử cha bấm được). */
  onClick?: () => void;
  /** Gợi ý thêm sau tên trong tooltip, ví dụ "Bấm để giao lại". */
  hint?: string;
}

const BASE =
  "inline-flex size-6 shrink-0 items-center justify-center rounded-full text-crm-xs font-medium outline-none focus-visible:ring-2 focus-visible:ring-crm-focus";

/** Hình tròn chữ cái đầu, màu theo tên (cùng tên luôn cùng màu); rê chuột hiện tên đầy đủ. */
export const Avatar = ({ name, className, onClick, hint }: Props) => {
  const classes = cn(
    BASE,
    colorOf(name),
    onClick && "cursor-pointer hover:ring-2 hover:ring-crm-line",
    className,
  );
  const label = hint ? `${name} · ${hint}` : name;

  return (
    <Tooltip content={label}>
      {onClick ? (
        <button
          type="button"
          aria-label={label}
          className={classes}
          onClick={(event) => {
            // Thẻ chứa avatar bấm được để mở chi tiết: không để cú bấm này lan lên
            event.stopPropagation();
            onClick();
          }}
        >
          {initialsOf(name)}
        </button>
      ) : (
        <span role="img" aria-label={label} tabIndex={0} className={classes}>
          {initialsOf(name)}
        </span>
      )}
    </Tooltip>
  );
};
