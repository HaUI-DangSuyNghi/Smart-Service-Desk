import { IconChevronLeft, IconChevronRight } from "@tabler/icons-react";
import { cn } from "cn";
import { Button } from "@/platform/shared/ui/button";
import { calendarWeek, parseCalendarDate, shiftCalendarDate } from "./calendarModel";
import { WorkDatePicker } from "./WorkDatePicker";

interface Props {
  selected: string;
  today: string;
  counts?: ReadonlyMap<string, number>;
  onSelect: (date: string) => void;
}

export const CalendarDays = ({ selected, today, counts, onSelect }: Props) => (
  <section
    aria-label="Chọn ngày làm việc"
    className="min-w-0 rounded-crm-md border border-crm-line"
  >
    <div className="flex flex-wrap items-center justify-between gap-3 border-b border-crm-line px-3 py-3 sm:px-4">
      <div className="min-w-0">
        <h2 className="text-crm-lg font-medium text-crm-text">Lịch của tôi</h2>
        <p className="mt-1 text-crm-sm text-crm-text-3">
          {parseCalendarDate(selected).toLocaleDateString("vi-VN", {
            weekday: "long",
            day: "2-digit",
            month: "long",
            year: "numeric",
          })}
        </p>
      </div>
      <div className="flex w-full min-w-0 flex-wrap items-center gap-1.5 sm:w-auto">
        <Button
          variant="outline"
          size="icon-sm"
          className="size-9 md:size-7"
          aria-label="Tuần trước"
          onClick={() => onSelect(shiftCalendarDate(selected, -7))}
        >
          <IconChevronLeft size={16} />
        </Button>
        <Button
          variant="outline"
          size="sm"
          className="min-h-9 md:min-h-7"
          onClick={() => onSelect(today)}
        >
          Hôm nay
        </Button>
        <Button
          variant="outline"
          size="icon-sm"
          className="size-9 md:size-7"
          aria-label="Tuần sau"
          onClick={() => onSelect(shiftCalendarDate(selected, 7))}
        >
          <IconChevronRight size={16} />
        </Button>
        <WorkDatePicker selected={selected} today={today} onSelect={onSelect} />
      </div>
    </div>
    <div className="grid grid-cols-7 gap-1 p-2" role="group" aria-label="Các ngày trong tuần">
      {calendarWeek(selected).map((day, index) => {
        const count = counts?.get(day) ?? 0;
        return (
          <button
            key={day}
            type="button"
            aria-pressed={selected === day}
            aria-current={day === today ? "date" : undefined}
            aria-label={`Xem ngày ${parseCalendarDate(day).toLocaleDateString("vi-VN")}${counts ? `, ${count} công việc` : ""}`}
            onClick={() => onSelect(day)}
            className={cn(
              "flex min-w-0 flex-col items-center gap-1 rounded-crm-sm px-1 py-2 outline-none transition-colors duration-100 focus-visible:ring-2 focus-visible:ring-crm-focus",
              selected === day
                ? "bg-crm-accent-soft text-crm-accent"
                : "text-crm-text-2 hover:bg-crm-hover",
            )}
          >
            <span className="text-crm-xs">{index === 6 ? "CN" : `T${index + 2}`}</span>
            <span
              className={cn(
                "flex size-7 items-center justify-center rounded-crm-sm text-crm-lg",
                day === today && "bg-crm-accent font-medium text-white",
              )}
            >
              {parseCalendarDate(day).getDate()}
            </span>
            <span className="text-crm-xs tabular-nums">{counts ? count : "—"}</span>
          </button>
        );
      })}
    </div>
  </section>
);
