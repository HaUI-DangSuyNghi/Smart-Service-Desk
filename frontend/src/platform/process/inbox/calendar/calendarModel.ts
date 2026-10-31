import type { QueueItem, WorkState } from "@/platform/services";

export type CalendarBucket = "day" | "overdue" | "undated";
export type CalendarState = WorkState | "";
const PRIORITY_ORDER = { CRITICAL: 0, HIGH: 1, MEDIUM: 2, LOW: 3 };

export const calendarDate = (value: Date | number) => {
  const date = new Date(value);
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, "0")}-${String(date.getDate()).padStart(2, "0")}`;
};

export const parseCalendarDate = (value: string) => new Date(`${value}T12:00:00`);

export const shiftCalendarDate = (value: string, days: number) => {
  const date = parseCalendarDate(value);
  date.setDate(date.getDate() + days);
  return calendarDate(date);
};

export const calendarWeek = (selected: string) => {
  const day = parseCalendarDate(selected).getDay();
  const monday = shiftCalendarDate(selected, -((day + 6) % 7));
  return Array.from({ length: 7 }, (_, index) => shiftCalendarDate(monday, index));
};

export const workDeadline = (item: QueueItem) => {
  const value = item.sla?.resolveBy;
  return value && Number.isFinite(Date.parse(value)) ? value : null;
};

export const isWorkOverdue = (item: QueueItem, now: number) => {
  const deadline = workDeadline(item);
  return Boolean(
    deadline &&
    (item.sla?.resolveBreached || (!item.sla?.clockPaused && Date.parse(deadline) < now)),
  );
};

const normalize = (text: string) =>
  text
    .normalize("NFD")
    .replace(/[\u0300-\u036f]/g, "")
    .replace(/đ/g, "d")
    .replace(/Đ/g, "D")
    .toLocaleLowerCase("vi");

export const filterCalendarWork = (
  items: readonly QueueItem[],
  search: string,
  state: CalendarState,
) => {
  const term = normalize(search.trim());
  return items.filter(
    (item) =>
      (!state || item.workState === state) &&
      (!term ||
        normalize(`${item.label} ${item.name} ${item.businessKey} ${item.processKey}`).includes(
          term,
        )),
  );
};

export const workInBucket = (
  item: QueueItem,
  bucket: CalendarBucket,
  date: string,
  now: number,
) => {
  const deadline = workDeadline(item);
  if (!deadline) return bucket === "undated";
  if (bucket === "overdue") return isWorkOverdue(item, now);
  return bucket === "day" && calendarDate(new Date(deadline)) === date;
};

const deadlineTime = (item: QueueItem) => {
  const deadline = workDeadline(item);
  return deadline ? Date.parse(deadline) : Infinity;
};

export const sortCalendarWork = (items: readonly QueueItem[]) =>
  [...items].sort(
    (left, right) =>
      deadlineTime(left) - deadlineTime(right) ||
      PRIORITY_ORDER[left.priority] - PRIORITY_ORDER[right.priority] ||
      left.id.localeCompare(right.id),
  );
