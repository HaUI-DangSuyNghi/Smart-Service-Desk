import type { FieldPath } from "react-hook-form";
import { z } from "zod";

// Form cấu hình chung (mục 6 của plan), khớp WorkSettings trong contracts/hpdi/openapi-target.yaml

const minutes = z.coerce.number({ message: "Nhập số" }).int("Nhập số nguyên").min(0, "Không âm");
const positiveMinutes = minutes.min(1, "Ít nhất 1");
const ratio = z.coerce.number({ message: "Nhập số" }).min(0, "Từ 0").max(1, "Tới 1");
const time = z.string().regex(/^([01]\d|2[0-3]):[0-5]\d$/, "Dạng giờ:phút, ví dụ 07:00");
const target = z.object({
  startWithinMinutes: positiveMinutes,
  resolveWithinMinutes: positiveMinutes,
});

export const workSettingsSchema = z.object({
  version: z.number(),
  assignment: z.object({
    pendingLoadFactor: ratio,
    tieThresholdMinutes: minutes,
    maxInProgress: positiveMinutes,
    minRemainingMinutes: minutes,
  }),
  shifts: z
    .array(z.object({ name: z.string().trim().min(1, "Nhập tên ca"), start: time, end: time }))
    .min(1),
  sla: z.object({
    targets: z.object({ LOW: target, MEDIUM: target, HIGH: target, CRITICAL: target }),
    escalateAfterRatio: ratio,
    maxEscalatedPriority: z.enum(["LOW", "MEDIUM", "HIGH", "CRITICAL"]),
    waitingExternalCounts: z.boolean(),
  }),
  critical: z.object({ autoQueueAfterMinutes: positiveMinutes }),
  alerts: z.object({
    warnRemainingRatio: ratio,
    pendingRemindDays: positiveMinutes,
    reopenUnclaimedMinutes: positiveMinutes,
  }),
  closing: z.object({ autoCloseAfterDays: positiveMinutes }),
  estimate: z.object({
    minSamples: positiveMinutes,
    windowMonths: positiveMinutes,
    outlierMinMinutes: minutes,
    outlierMaxMinutes: positiveMinutes,
  }),
});

export type WorkSettingsInput = z.input<typeof workSettingsSchema>;
export type WorkSettingsValues = z.output<typeof workSettingsSchema>;

export interface NumberFieldDef {
  path: FieldPath<WorkSettingsInput>;
  label: string;
  /** Tỉ lệ 0–1 thì bước 0,05. */
  step?: number;
  rule: string;
}

export interface FieldGroup {
  title: string;
  fields: NumberFieldDef[];
}

/** Nhóm ô số theo bảng mục 6; ca trực và bảng hạn xử lý vẽ riêng. */
export const NUMBER_GROUPS: readonly FieldGroup[] = [
  {
    title: "Chọn người xử lý",
    fields: [
      { path: "assignment.pendingLoadFactor", label: "Hệ số tải của việc Đang chờ", step: 0.05, rule: "R2" },
      { path: "assignment.tieThresholdMinutes", label: "Ngưỡng coi như bằng nhau (phút)", rule: "R2" },
      { path: "assignment.minRemainingMinutes", label: "Phần còn lại tối thiểu (phút)", rule: "R2" },
      { path: "assignment.maxInProgress", label: "Tối đa việc Đang làm mỗi người", rule: "R6" },
    ],
  },
  {
    title: "Hạn xử lý",
    fields: [
      { path: "sla.escalateAfterRatio", label: "Chờ quá tỉ lệ này thì nâng 1 bậc", step: 0.05, rule: "R5" },
      { path: "critical.autoQueueAfterMinutes", label: "Khẩn cấp tự vào hàng đợi sau (phút)", rule: "R7" },
    ],
  },
  {
    title: "Cảnh báo và tự đóng",
    fields: [
      { path: "alerts.warnRemainingRatio", label: "Cảnh báo khi còn lại (tỉ lệ)", step: 0.05, rule: "R8" },
      { path: "alerts.pendingRemindDays", label: "Nhắc việc Đang chờ sau (ngày)", rule: "R8" },
      { path: "alerts.reopenUnclaimedMinutes", label: "Cảnh báo việc mở lại chưa nhận (phút)", rule: "R11" },
      { path: "closing.autoCloseAfterDays", label: "Tự đóng nếu không xác nhận (ngày)", rule: "R11" },
    ],
  },
  {
    title: "Thời gian ước tính",
    fields: [
      { path: "estimate.minSamples", label: "Số việc tối thiểu để dùng trung vị", rule: "R2" },
      { path: "estimate.windowMonths", label: "Lấy mẫu trong (tháng gần nhất)", rule: "R2" },
      { path: "estimate.outlierMinMinutes", label: "Bỏ mẫu dưới (phút)", rule: "R2" },
      { path: "estimate.outlierMaxMinutes", label: "Bỏ mẫu trên (phút)", rule: "R2" },
    ],
  },
];
