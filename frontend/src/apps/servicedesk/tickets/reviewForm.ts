import { z } from "zod";
import { WAIT_FOR_SHIFT } from "../../../platform/process";
import type { Assessment, ReviewDecision } from "../services/ticketService";
import {
  DEFAULT_ESTIMATES,
  MAX_ESTIMATE_MINUTES,
  MAX_REASON,
  priorityOf,
  type Category,
} from "./ticketRules";

// Form duyệt ticket (R4), khớp ReviewDecision trong contracts/openapi/servicedesk-target.yaml

export const reviewSchema = z.object({
  category: z.enum(["HARDWARE", "NETWORK", "SOFTWARE", "ACCOUNT", "OTHER"], {
    message: "Chọn loại sự cố",
  }),
  priority: z.enum(["LOW", "MEDIUM", "HIGH", "CRITICAL"], { message: "Chọn mức ưu tiên" }),
  estimateMinutes: z.coerce
    .number({ message: "Nhập số phút" })
    .int("Nhập số nguyên")
    .min(1, "Ít nhất 1 phút")
    .max(MAX_ESTIMATE_MINUTES, `Tối đa ${MAX_ESTIMATE_MINUTES} phút`),
  /** subject; WAIT_FOR_SHIFT là để chờ ca sau. */
  assignee: z.string({ message: "Chọn người xử lý hoặc để chờ ca sau" }),
  reason: z.string().trim().max(MAX_REASON, `Tối đa ${MAX_REASON} ký tự`),
});

export type ReviewInput = z.input<typeof reviewSchema>;
export type ReviewValues = z.output<typeof reviewSchema>;

/** Điền sẵn từ gợi ý AI; AI không chắc thì để trống cho điều phối viên chọn. */
export const reviewDefaults = (assessment?: Assessment | null): Partial<ReviewInput> => {
  const category = assessment?.category ?? undefined;
  const priority = assessment?.priority ?? undefined;
  return {
    category,
    priority,
    estimateMinutes: category && priority ? DEFAULT_ESTIMATES[category][priority] : undefined,
    reason: "",
  };
};

/** Ưu tiên theo bảng ảnh hưởng × độ gấp của AI, để gợi ý cạnh ô chọn. */
export const suggestedPriority = (assessment?: Assessment | null) =>
  assessment?.impact && assessment.urgency
    ? priorityOf(assessment.impact, assessment.urgency)
    : null;

/** Có khác gợi ý AI không: khác thì bắt buộc ghi lý do (R9). */
export const differsFromAi = (
  values: { category: Category; priority: ReviewValues["priority"] },
  assessment?: Assessment | null,
) =>
  Boolean(assessment?.category) &&
  (values.category !== assessment?.category || values.priority !== assessment?.priority);

export const toDecision = (values: ReviewValues, assessment?: Assessment | null): ReviewDecision => ({
  category: values.category,
  impact: assessment?.impact ?? undefined,
  urgency: assessment?.urgency ?? undefined,
  priority: values.priority,
  estimateMinutes: values.estimateMinutes,
  assignee: values.assignee === WAIT_FOR_SHIFT ? null : values.assignee,
  reason: values.reason || undefined,
});
