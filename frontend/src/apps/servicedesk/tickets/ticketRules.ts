import type { Priority } from "../../../platform/services";
import type { Intake } from "../services/intakeService";
import type { Assessment, TicketState, TicketView } from "../services/ticketService";

// Luật nghiệp vụ thuần của ticket (plan ticket.md): R1 chọn tổ, mục 4 bảng ưu tiên, R2 ET mặc định

export type Category = "HARDWARE" | "NETWORK" | "SOFTWARE" | "ACCOUNT" | "OTHER";
export type Impact = "I1" | "I2" | "I3" | "I4";
export type Urgency = "U1" | "U2" | "U3" | "U4";

export const CATEGORIES: readonly Category[] = [
  "HARDWARE",
  "NETWORK",
  "SOFTWARE",
  "ACCOUNT",
  "OTHER",
];
export const PRIORITIES: readonly Priority[] = ["LOW", "MEDIUM", "HIGH", "CRITICAL"];
export const IMPACTS: readonly Impact[] = ["I1", "I2", "I3", "I4"];
export const URGENCIES: readonly Urgency[] = ["U1", "U2", "U3", "U4"];

// Loại lỗi → tổ (R1) và ET mặc định (R2) nằm ở Cấu hình hệ thống, FE đọc từ backend

/** Mục 4: ưu tiên = ảnh hưởng × độ gấp. */
const PRIORITY_MATRIX: Readonly<Record<Impact, Readonly<Record<Urgency, Priority>>>> = {
  I1: { U1: "LOW", U2: "LOW", U3: "MEDIUM", U4: "HIGH" },
  I2: { U1: "LOW", U2: "MEDIUM", U3: "HIGH", U4: "HIGH" },
  I3: { U1: "MEDIUM", U2: "HIGH", U3: "HIGH", U4: "CRITICAL" },
  I4: { U1: "HIGH", U2: "HIGH", U3: "CRITICAL", U4: "CRITICAL" },
};

export const priorityOf = (impact: Impact, urgency: Urgency): Priority =>
  PRIORITY_MATRIX[impact][urgency];

/** Bảng ET mặc định (phút làm) theo loại × ưu tiên, sửa ở Cấu hình hệ thống. */
export type EstimateTable = Record<Category, Record<Priority, number>>;

export const MAX_TITLE = 200;
export const MAX_DESCRIPTION = 4000;
export const MAX_REASON = 1000;
/** Một tuần, khớp maximum của estimateMinutes trong contract. */
export const MAX_ESTIMATE_MINUTES = 10080;

/** Bước xử lý: còn sửa được phân loại, đổi người. */
export const HANDLING_STATES: ReadonlySet<TicketState> = new Set([
  "ASSIGNED",
  "IN_PROGRESS",
  "PENDING",
  "REOPENED",
]);

/**
 * Gợi ý AI dùng được để điền sẵn / sắp xếp: chỉ khi READY và không phải INSUFFICIENT_INFORMATION
 * (khi đó OTHER/MEDIUM chỉ là giá trị mặc định, không phải gợi ý).
 */
export const aiSuggestion = (assessment?: Assessment | null) => {
  const result = assessment?.status === "READY" ? assessment.result : null;
  return result && result.reasonCode !== "INSUFFICIENT_INFORMATION" ? result : null;
};

/**
 * Đang ở bước duyệt: chờ duyệt lần đầu, hoặc Mở lại (người gửi báo chưa xong) mà chưa duyệt lại.
 * Mở lại sau khi đã giao lại thì có workState (bước xử lý), không phải bước duyệt.
 */
export const awaitsReview = (ticket: Pick<TicketView, "state" | "workState">) =>
  ticket.state === "AWAITING_REVIEW" || (ticket.state === "REOPENED" && !ticket.workState);

/** Đang ở bước xử lý: còn sửa được phân loại, đổi người, Bắt đầu / Tạm dừng. */
export const isHandling = (ticket: Pick<TicketView, "state" | "workState">) =>
  ticket.state !== undefined && HANDLING_STATES.has(ticket.state) && !awaitsReview(ticket);

/** Đã bắt đầu làm (đang làm, đang chờ, hoặc đã có thời gian làm): hạn tính tới hạn xong. */
export const isStarted = (ticket: Pick<TicketView, "state" | "workedMinutes">) =>
  ticket.state === "IN_PROGRESS" || ticket.state === "PENDING" || (ticket.workedMinutes ?? 0) > 0;

// Đã sang bước duyệt (nút "Duyệt và giao việc") hoặc đã xong: không tiếp nhận nữa
const PAST_INTAKE: ReadonlySet<Intake["status"]> = new Set([
  "REVIEW_RELEASED",
  "REVIEWED",
  "CANONICAL_FOLLOWER",
  "CLOSED",
  "CANCELLED",
]);

/** Còn tiếp nhận thủ công (chuyển sang Chờ duyệt) được. */
export const canReleaseIntake = (state: Pick<Intake, "status">) => !PAST_INTAKE.has(state.status);

/** Còn gộp / tách ticket trùng được: chưa có bước duyệt, chưa duyệt. */
export const canLinkCanonical = (state: Pick<Intake, "status" | "reviewTaskMayExist">) =>
  !state.reviewTaskMayExist && state.status !== "REVIEWED";
