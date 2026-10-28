import { z } from "zod";
import type { AgentSchedule } from "@/platform/services";

// Giới hạn khớp backend (AgentScheduleService): ca dài tối đa 24 giờ, không chồng nhau, tối đa 366 ca
export const MAX_SHIFTS = 366;
const MAX_SHIFT_MS = 24 * 60 * 60 * 1000;

const pad = (value: number) => String(value).padStart(2, "0");

/** ISO → giá trị ô datetime-local theo giờ máy ("2026-10-28T07:00"). */
export const toLocalInput = (iso: string) => {
  const date = new Date(iso);
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(
    date.getHours(),
  )}:${pad(date.getMinutes())}`;
};

/** Giá trị ô datetime-local (giờ máy) → ISO UTC gửi backend. */
export const fromLocalInput = (value: string) => new Date(value).toISOString();

const shiftSchema = z
  .object({ startsAt: z.string().min(1, "Chọn giờ bắt đầu"), endsAt: z.string().min(1, "Chọn giờ kết thúc") })
  .refine((shift) => new Date(shift.endsAt) > new Date(shift.startsAt), {
    path: ["endsAt"],
    message: "Kết thúc phải sau bắt đầu",
  })
  .refine(
    (shift) => new Date(shift.endsAt).getTime() - new Date(shift.startsAt).getTime() <= MAX_SHIFT_MS,
    { path: ["endsAt"], message: "Một ca tối đa 24 giờ" },
  );

export const scheduleSchema = z
  .object({
    enabled: z.boolean(),
    shifts: z.array(shiftSchema).max(MAX_SHIFTS, `Tối đa ${MAX_SHIFTS} ca`),
  })
  .superRefine(({ shifts }, context) => {
    const sorted = shifts
      .map((shift, index) => ({ index, start: new Date(shift.startsAt), end: new Date(shift.endsAt) }))
      .sort((a, b) => a.start.getTime() - b.start.getTime());
    for (let i = 1; i < sorted.length; i++) {
      if (sorted[i].start < sorted[i - 1].end)
        context.addIssue({
          code: "custom",
          path: ["shifts", sorted[i].index, "startsAt"],
          message: "Chồng lên ca khác",
        });
    }
  });

export type ScheduleValues = z.infer<typeof scheduleSchema>;

/** Lịch từ backend → giá trị form (giờ máy), ca sắp theo thời gian. */
export const scheduleValues = (schedule: AgentSchedule): ScheduleValues => ({
  enabled: schedule.enabled,
  shifts: [...schedule.shifts]
    .sort((a, b) => a.startsAt.localeCompare(b.startsAt))
    .map((shift) => ({ startsAt: toLocalInput(shift.startsAt), endsAt: toLocalInput(shift.endsAt) })),
});

/** Giá trị form → body PUT; gửi kèm version đang xem để backend chặn ghi đè (409). */
export const toSchedule = (schedule: AgentSchedule, values: ScheduleValues): AgentSchedule => ({
  subject: schedule.subject,
  version: schedule.version,
  enabled: values.enabled,
  shifts: values.shifts.map((shift) => ({
    startsAt: fromLocalInput(shift.startsAt),
    endsAt: fromLocalInput(shift.endsAt),
  })),
});

/** Ca đang diễn ra hoặc gần nhất sắp tới, để hiện trên bảng. */
export const nextShift = (schedule: AgentSchedule, now = new Date()) =>
  [...schedule.shifts]
    .filter((shift) => new Date(shift.endsAt) > now)
    .sort((a, b) => a.startsAt.localeCompare(b.startsAt))[0] ?? null;
