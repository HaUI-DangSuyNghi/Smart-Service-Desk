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
  /** Giải thích hiện khi rê chuột vào nhãn. */
  hint?: string;
}

// Nhóm ô số theo trình tự xử lý một ticket; ca trực và bảng hạn xử lý vẽ riêng
export const ASSIGNMENT_FIELDS: readonly NumberFieldDef[] = [
  {
    path: "assignment.pendingLoadFactor",
    label: "Hệ số tải việc Đang chờ",
    step: 0.05,
    hint: "Việc đang tạm dừng chỉ tính một phần vào tải của người đó. 0,5 nghĩa là tính một nửa phần còn lại.",
  },
  {
    path: "assignment.tieThresholdMinutes",
    label: "Ngưỡng ngang tải (phút)",
    hint: "Hai người có tải chênh nhau dưới số phút này thì coi như bằng nhau, khi đó chọn người hôm nay nhận ít việc hơn.",
  },
  {
    path: "assignment.minRemainingMinutes",
    label: "Thời gian còn lại tối thiểu (phút)",
    hint: "Việc Đang làm luôn tính vào tải ít nhất số phút này, kể cả khi sắp xong.",
  },
  {
    path: "assignment.maxInProgress",
    label: "Tối đa việc Đang làm mỗi người",
    hint: "Số ticket một người được Đang làm cùng lúc.",
  },
];

export const ESTIMATE_FIELDS: readonly NumberFieldDef[] = [
  {
    path: "estimate.minSamples",
    label: "Số mẫu tối thiểu",
    hint: "Cần ít nhất bấy nhiêu ticket đã đóng cùng loại và ưu tiên thì mới dùng trung vị thay cho bảng mặc định.",
  },
  {
    path: "estimate.windowMonths",
    label: "Khoảng lấy mẫu (tháng)",
    hint: "Chỉ lấy ticket đã đóng trong bấy nhiêu tháng gần nhất để tính trung vị.",
  },
  {
    path: "estimate.outlierMinMinutes",
    label: "Bỏ mẫu dưới (phút)",
    hint: "Thời gian làm dưới mức này thường do quên bấm Bắt đầu, nên bị bỏ khỏi mẫu.",
  },
  {
    path: "estimate.outlierMaxMinutes",
    label: "Bỏ mẫu trên (phút)",
    hint: "Thời gian làm trên mức này thường do quên bấm Tạm dừng, nên bị bỏ khỏi mẫu.",
  },
];

export const SLA_FIELDS: readonly NumberFieldDef[] = [
  {
    path: "sla.escalateAfterRatio",
    label: "Tỉ lệ chờ để nâng 1 bậc",
    step: 0.05,
  },
  {
    path: "critical.autoQueueAfterMinutes",
    label: "Khẩn cấp tự vào hàng đợi (phút)",
  },
];

export const ALERT_FIELDS: readonly NumberFieldDef[] = [
  {
    path: "alerts.warnRemainingRatio",
    label: "Cảnh báo khi còn lại (tỉ lệ)",
    step: 0.05,
    hint: "Khi thời gian SLA còn lại bằng tỉ lệ này thì cảnh báo người xử lý và điều phối viên. 0,2 là còn 20%.",
  },
  {
    path: "alerts.pendingRemindDays",
    label: "Nhắc việc Đang chờ (ngày)",
    hint: "Việc Đang chờ quá số ngày này thì nhắc kỹ thuật viên.",
  },
  {
    path: "alerts.reopenUnclaimedMinutes",
    label: "Cảnh báo mở lại chưa nhận (phút)",
    hint: "Ticket Mở lại quá số phút này chưa ai nhận thì báo điều phối viên.",
  },
];

export const CLOSING_FIELDS: readonly NumberFieldDef[] = [
  {
    path: "closing.autoCloseAfterDays",
    label: "Tự đóng sau (ngày)",
    hint: "Ticket Chờ xác nhận mà người yêu cầu không phản hồi sau số ngày này thì tự đóng.",
  },
];
