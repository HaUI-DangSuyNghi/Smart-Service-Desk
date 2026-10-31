import { useState } from "react";
import {
  IconCalendar,
  IconChevronDown,
  IconChevronLeft,
  IconChevronRight,
} from "@tabler/icons-react";
import { cn } from "cn";
import { Popover } from "radix-ui";
import { Button } from "@/platform/shared/ui/button";
import { calendarDate, calendarWeek, parseCalendarDate, shiftCalendarDate } from "./calendarModel";

interface Props {
  selected: string;
  today: string;
  onSelect: (date: string) => void;
}

export const WorkDatePicker = ({ selected, today, onSelect }: Props) => {
  const [open, setOpen] = useState(false);
  const [month, setMonth] = useState(() => selected.slice(0, 7));
  const first = `${month}-01`;
  const start = calendarWeek(first)[0];
  const monthLabel = parseCalendarDate(first).toLocaleDateString("vi-VN", {
    month: "long",
    year: "numeric",
  });
  const handleSelect = (value: string) => {
    onSelect(value);
    setOpen(false);
  };
  const handleMonthChange = (offset: number) => {
    const date = parseCalendarDate(first);
    date.setMonth(date.getMonth() + offset);
    setMonth(calendarDate(date).slice(0, 7));
  };
  return (
    <Popover.Root
      open={open}
      onOpenChange={(next) => {
        if (next) setMonth(selected.slice(0, 7));
        setOpen(next);
      }}
    >
      <Popover.Trigger asChild>
        <Button
          variant="outline"
          size="sm"
          className="min-h-9 gap-2 md:min-h-7"
          aria-label="Ngày xem lịch"
        >
          <IconCalendar size={16} className="text-crm-text-3" />
          <span>
            {parseCalendarDate(selected).toLocaleDateString("vi-VN", {
              day: "2-digit",
              month: "2-digit",
              year: "numeric",
            })}
          </span>
          <IconChevronDown size={14} className="text-crm-text-3" />
        </Button>
      </Popover.Trigger>
      <Popover.Portal>
        <Popover.Content
          align="end"
          sideOffset={8}
          collisionPadding={16}
          aria-label="Chọn ngày xem lịch"
          className="z-50 w-72 max-w-[calc(100vw-2rem)] rounded-crm-md border border-crm-line bg-crm-surface p-3 text-crm-md text-crm-text shadow-crm-strong outline-none"
        >
          <div className="mb-3 flex items-center justify-between gap-2">
            <Button
              variant="ghost"
              size="icon-sm"
              className="size-9"
              aria-label="Tháng trước"
              onClick={() => handleMonthChange(-1)}
            >
              <IconChevronLeft size={16} />
            </Button>
            <span aria-live="polite" className="text-crm-sm font-medium capitalize">
              {monthLabel}
            </span>
            <Button
              variant="ghost"
              size="icon-sm"
              className="size-9"
              aria-label="Tháng sau"
              onClick={() => handleMonthChange(1)}
            >
              <IconChevronRight size={16} />
            </Button>
          </div>
          <div
            className="mb-1 grid grid-cols-7 text-center text-crm-xs text-crm-text-3"
            aria-hidden
          >
            {["T2", "T3", "T4", "T5", "T6", "T7", "CN"].map((label) => (
              <span key={label} className="py-1">
                {label}
              </span>
            ))}
          </div>
          <div role="group" aria-label={monthLabel} className="grid grid-cols-7 gap-y-1">
            {Array.from({ length: 42 }, (_, index) => {
              const day = shiftCalendarDate(start, index);
              return (
                <button
                  key={day}
                  type="button"
                  aria-label={`Chọn ngày ${parseCalendarDate(day).toLocaleDateString("vi-VN")}`}
                  aria-pressed={day === selected}
                  aria-current={day === today ? "date" : undefined}
                  onClick={() => handleSelect(day)}
                  className={cn(
                    "flex h-9 min-w-0 items-center justify-center rounded-crm-sm text-crm-sm outline-none transition-colors duration-100 focus-visible:ring-2 focus-visible:ring-crm-focus",
                    day === selected
                      ? "bg-crm-accent font-medium text-white"
                      : [
                          "hover:bg-crm-hover",
                          day.startsWith(month) ? "text-crm-text" : "text-crm-text-light",
                        ],
                    day === today &&
                      day !== selected &&
                      "bg-crm-accent-soft font-medium text-crm-accent",
                  )}
                >
                  {parseCalendarDate(day).getDate()}
                </button>
              );
            })}
          </div>
          <div className="mt-3 border-t border-crm-line pt-2">
            <Button
              variant="ghost"
              size="sm"
              className="min-h-9 w-full text-crm-accent"
              onClick={() => handleSelect(today)}
            >
              Về hôm nay
            </Button>
          </div>
        </Popover.Content>
      </Popover.Portal>
    </Popover.Root>
  );
};
