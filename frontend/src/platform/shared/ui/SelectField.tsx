import { Fragment } from "react";
import { IconCheck, IconChevronDown } from "@tabler/icons-react";
import { cn } from "cn";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "./dropdown-menu";

interface Props {
  options: readonly { value: string; label: string }[];
  value: string;
  onValueChange: (value: string) => void;
  /** Lựa chọn đầu tiên, value rỗng, nghĩa là "không lọc", ví dụ "Mọi thao tác". */
  placeholder?: string;
  "aria-label": string;
  className?: string;
}

/** Ô chọn gọn trên toolbar, cùng kiểu dropdown với menu tài khoản; đang lọc thì chữ đậm hơn. */
export const SelectField = ({
  options,
  value,
  onValueChange,
  placeholder,
  className,
  ...props
}: Props) => {
  const selected = options.find((option) => option.value === value);
  const choices =
    placeholder === undefined ? options : [{ value: "", label: placeholder }, ...options];

  return (
    <DropdownMenu>
      <DropdownMenuTrigger
        aria-label={props["aria-label"]}
        className={cn(
          "flex h-8 max-w-48 min-w-0 items-center gap-1.5 rounded-crm-sm border border-crm-line bg-crm-surface px-2 outline-none sm:h-7",
          "transition-colors duration-100 hover:bg-crm-hover focus-visible:border-crm-focus data-[state=open]:bg-crm-hover",
          selected ? "text-crm-text" : "text-crm-text-2",
          className,
        )}
      >
        <span className="truncate">{selected?.label ?? placeholder}</span>
        <IconChevronDown size={14} stroke={2} className="shrink-0 text-crm-text-3" />
      </DropdownMenuTrigger>

      <DropdownMenuContent
        align="start"
        className="w-auto max-w-72 min-w-(--radix-dropdown-menu-trigger-width)"
      >
        {choices.map((option, index) => (
          <Fragment key={option.value}>
            <DropdownMenuItem onSelect={() => onValueChange(option.value)}>
              <span className="flex-1 truncate">{option.label}</span>
              {option.value === value && <IconCheck size={14} stroke={2} />}
            </DropdownMenuItem>
            {/* Tách "không lọc" khỏi các lựa chọn thật */}
            {placeholder !== undefined && index === 0 && <DropdownMenuSeparator />}
          </Fragment>
        ))}
      </DropdownMenuContent>
    </DropdownMenu>
  );
};
