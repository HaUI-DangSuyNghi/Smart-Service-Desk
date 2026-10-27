import type { Priority } from "../../../platform/services";
import type { TicketState, TicketView } from "../services/ticketService";

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

/** R1: loại lỗi → tổ (group Keycloak trong workspace); OTHER do điều phối viên tự chọn. */
export const DEFAULT_CATEGORY_GROUPS: Readonly<Record<Category, string | null>> = {
  HARDWARE: "hardware",
  NETWORK: "network",
  SOFTWARE: "software",
  ACCOUNT: "sysadmin",
  OTHER: null,
};

/** Mục 4: ưu tiên = ảnh hưởng × độ gấp. */
const PRIORITY_MATRIX: Readonly<Record<Impact, Readonly<Record<Urgency, Priority>>>> = {
  I1: { U1: "LOW", U2: "LOW", U3: "MEDIUM", U4: "HIGH" },
  I2: { U1: "LOW", U2: "MEDIUM", U3: "HIGH", U4: "HIGH" },
  I3: { U1: "MEDIUM", U2: "HIGH", U3: "HIGH", U4: "CRITICAL" },
  I4: { U1: "HIGH", U2: "HIGH", U3: "CRITICAL", U4: "CRITICAL" },
};

export const priorityOf = (impact: Impact, urgency: Urgency): Priority =>
  PRIORITY_MATRIX[impact][urgency];

export type EstimateTable = Record<Category, Record<Priority, number>>;

/** R2: ET mặc định (phút làm) theo loại × ưu tiên, dùng khi chưa đủ dữ liệu. */
export const DEFAULT_ESTIMATES: Readonly<EstimateTable> = {
  HARDWARE: { LOW: 45, MEDIUM: 60, HIGH: 90, CRITICAL: 120 },
  NETWORK: { LOW: 30, MEDIUM: 45, HIGH: 60, CRITICAL: 90 },
  SOFTWARE: { LOW: 30, MEDIUM: 30, HIGH: 45, CRITICAL: 60 },
  ACCOUNT: { LOW: 15, MEDIUM: 15, HIGH: 20, CRITICAL: 30 },
  OTHER: { LOW: 30, MEDIUM: 45, HIGH: 60, CRITICAL: 90 },
};

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

/** Đã bắt đầu làm (đang làm, đang chờ, hoặc đã có thời gian làm): hạn tính tới hạn xong. */
export const isStarted = (ticket: Pick<TicketView, "state" | "workedMinutes">) =>
  ticket.state === "IN_PROGRESS" || ticket.state === "PENDING" || (ticket.workedMinutes ?? 0) > 0;
