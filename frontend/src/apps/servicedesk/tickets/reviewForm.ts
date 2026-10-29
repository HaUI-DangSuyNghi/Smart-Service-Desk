import { z } from "zod";
import { WAIT_FOR_SHIFT } from "../../../platform/process";
import type { AssessmentResult, ReviewDecision } from "../services/ticketService";
import {
  CATEGORIES,
  MAX_ESTIMATE_MINUTES,
  MAX_REASON,
  PRIORITIES,
  priorityOf,
  type Category,
} from "./ticketRules";

// Form duyệt ticket (R4), khớp TicketReview.Decision của backend

/** Lý do điền sẵn khi giữ nguyên gợi ý AI; backend luôn bắt buộc lý do. */
export const AGREE_WITH_AI = "Đồng ý với gợi ý của AI";

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
  reason: z.string().trim().min(1, "Ghi lý do duyệt").max(MAX_REASON, `Tối đa ${MAX_REASON} ký tự`),
});

export type ReviewInput = z.input<typeof reviewSchema>;

/** Phần gợi ý AI form cần: kết quả chạy nền hoặc đề xuất cuối của luồng tiếp nhận. */
export type Suggestion = Pick<AssessmentResult, "category" | "priority" | "impact" | "urgency">;

/** Đề xuất cuối của tiếp nhận (ai07, chuỗi tự do) → gợi ý; bỏ giá trị lạ. */
export const intakeSuggestion = (
  ai07?: { category?: string | null; priority?: string | null } | null,
): Suggestion | null => {
  const category = CATEGORIES.find((item) => item === ai07?.category) ?? null;
  const priority = PRIORITIES.find((item) => item === ai07?.priority) ?? null;
  return category || priority ? { category, priority, impact: null, urgency: null } : null;
};
export type ReviewValues = z.output<typeof reviewSchema>;

/**
 * Điền sẵn từ gợi ý AI (đã lọc bằng aiSuggestion); không có gợi ý thì để trống cho điều phối viên chọn.
 * ET điền sau khi tải ET mặc định của backend (bảng cấu hình hoặc trung vị việc tương tự).
 */
export const reviewDefaults = (suggestion?: Suggestion | null): Partial<ReviewInput> => {
  const category = suggestion?.category ?? undefined;
  return {
    category,
    priority: suggestion?.priority ?? undefined,
    reason: category ? AGREE_WITH_AI : "",
  };
};

/** Ưu tiên theo bảng ảnh hưởng × độ gấp của AI, để gợi ý cạnh ô chọn. */
export const suggestedPriority = (suggestion?: Suggestion | null) =>
  suggestion?.impact && suggestion.urgency
    ? priorityOf(suggestion.impact, suggestion.urgency)
    : null;

/** Có khác gợi ý AI không: khác thì phải ghi lý do thật, không dùng câu điền sẵn (R9). */
export const differsFromAi = (
  values: { category: Category; priority: ReviewValues["priority"] },
  suggestion?: Suggestion | null,
) =>
  Boolean(suggestion?.category) &&
  (values.category !== suggestion?.category || values.priority !== suggestion?.priority);

/** Ticket qua luồng tiếp nhận: gửi revision / version đang xem để backend chặn duyệt bản cũ. */
export interface IntakeVersion {
  contextRevision: number;
  version: number;
}

/**
 * defaultMinutes: ET backend tự dùng khi không gửi; bằng số này thì không gửi để backend ghi đúng
 * nguồn (bảng / trung vị) thay vì "điều phối viên đặt".
 */
export const toDecision = (
  values: ReviewValues,
  suggestion?: Suggestion | null,
  intake?: IntakeVersion | null,
  defaultMinutes?: number,
): ReviewDecision => ({
  category: values.category,
  // Chỉ gửi ảnh hưởng / độ gấp khi giữ đúng loại AI đề xuất
  ...(suggestion?.impact && values.category === suggestion.category
    ? { impact: suggestion.impact }
    : {}),
  ...(suggestion?.urgency && values.category === suggestion.category
    ? { urgency: suggestion.urgency }
    : {}),
  priority: values.priority,
  ...(values.estimateMinutes !== defaultMinutes ? { estimateMinutes: values.estimateMinutes } : {}),
  assignee: values.assignee === WAIT_FOR_SHIFT ? null : values.assignee,
  reason: values.reason,
  ...(intake
    ? { expectedContextRevision: intake.contextRevision, expectedIntakeVersion: intake.version }
    : {}),
});
