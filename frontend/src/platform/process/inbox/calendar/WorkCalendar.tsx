import { useState } from "react";
import { IconCalendarTime } from "@tabler/icons-react";
import { cn } from "cn";
import { Button } from "@/platform/shared/ui/button";
import { EmptyState } from "@/platform/shared/ui/EmptyState";
import { ErrorState } from "@/platform/shared/ui/ErrorState";
import { PageBody } from "@/platform/shared/ui/page";
import { useNow } from "../../work/useNow";
import { formatMinutes } from "../../work/workLabels";
import { CalendarDays } from "./CalendarDays";
import { useWorkCalendar } from "./useWorkCalendar";
import { WorkAgenda, WorkAgendaSkeleton } from "./WorkAgenda";
import { WorkCalendarToolbar } from "./WorkCalendarToolbar";
import {
  calendarDate,
  filterCalendarWork,
  sortCalendarWork,
  workDeadline,
  workInBucket,
  type CalendarBucket,
  type CalendarState,
} from "./calendarModel";

const BUCKET_LABELS = { day: "Trong ngày", overdue: "Quá hạn", undated: "Chưa có hạn" };
const EMPTY_LABELS = {
  day: "Không có công việc đến hạn trong ngày này.",
  overdue: "Không có công việc quá hạn.",
  undated: "Không có công việc chưa có hạn.",
};

export const WorkCalendar = () => {
  const query = useWorkCalendar();
  const now = useNow();
  const today = calendarDate(now);
  const [selected, setSelected] = useState<string | null>(null);
  const date = selected ?? today;
  const [bucket, setBucket] = useState<CalendarBucket>("day");
  const [search, setSearch] = useState("");
  const [state, setState] = useState<CalendarState>("");
  const available = filterCalendarWork(query.isSuccess ? query.data : [], search, state);
  const items = sortCalendarWork(available.filter((item) => workInBucket(item, bucket, date, now)));
  const counts = new Map<string, number>();
  for (const item of available) {
    const deadline = workDeadline(item);
    if (deadline) {
      const key = calendarDate(new Date(deadline));
      counts.set(key, (counts.get(key) ?? 0) + 1);
    }
  }
  const handleSelectDate = (value: string) => {
    setSelected(value === today ? null : value);
    setBucket("day");
  };
  return (
    <>
      <WorkCalendarToolbar
        search={search}
        state={state}
        isFetching={query.isFetching}
        onSearch={setSearch}
        onStateChange={setState}
        onRefresh={() => void query.refetch()}
      />
      <PageBody>
        <div className="flex w-full min-w-0 flex-col gap-5 p-4 lg:p-6">
          <CalendarDays
            selected={date}
            today={today}
            counts={query.isSuccess ? counts : undefined}
            onSelect={handleSelectDate}
          />
          <div role="group" aria-label="Nhóm công việc theo hạn" className="grid grid-cols-3 gap-2">
            {(["day", "overdue", "undated"] as const).map((value) => (
              <button
                key={value}
                type="button"
                aria-pressed={bucket === value}
                onClick={() => setBucket(value)}
                className={cn(
                  "flex min-w-0 flex-col items-start gap-1 rounded-crm-md border p-3 text-left outline-none transition-colors duration-100 focus-visible:ring-2 focus-visible:ring-crm-focus sm:flex-row sm:items-center sm:justify-between",
                  bucket === value
                    ? "border-crm-focus bg-crm-accent-soft text-crm-accent"
                    : "border-crm-line text-crm-text-2 hover:bg-crm-hover",
                )}
              >
                <span className="text-crm-xs sm:text-crm-sm">{BUCKET_LABELS[value]}</span>
                <span className="text-crm-lg font-medium tabular-nums">
                  {query.isSuccess
                    ? available.filter((item) => workInBucket(item, value, date, now)).length
                    : "—"}
                </span>
              </button>
            ))}
          </div>
          <section aria-label={BUCKET_LABELS[bucket]} className="min-w-0">
            <div className="mb-3 flex flex-wrap items-center justify-between gap-2 text-crm-sm">
              <h2 className="font-medium text-crm-text">{BUCKET_LABELS[bucket]}</h2>
              {query.isSuccess && (
                <span className="text-crm-text-3">
                  {items.length} việc · Ước tính{" "}
                  {formatMinutes(items.reduce((total, item) => total + item.remainingMinutes, 0))}
                </span>
              )}
            </div>
            {query.isPending ? (
              <WorkAgendaSkeleton />
            ) : query.error ? (
              <div className="text-center">
                <ErrorState error={query.error} />
                <Button
                  variant="outline"
                  className="min-h-9 md:min-h-7"
                  onClick={() => void query.refetch()}
                >
                  Thử lại
                </Button>
              </div>
            ) : items.length === 0 ? (
              <EmptyState
                icon={IconCalendarTime}
                title={
                  search || state ? "Không có công việc phù hợp bộ lọc." : EMPTY_LABELS[bucket]
                }
              />
            ) : (
              <WorkAgenda items={items} now={now} showDate={bucket === "overdue"} />
            )}
          </section>
        </div>
      </PageBody>
    </>
  );
};
