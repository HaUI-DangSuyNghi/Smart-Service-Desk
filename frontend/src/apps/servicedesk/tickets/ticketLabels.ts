import type { TagTone } from "../../../platform/shared/ui/Tag";
import type { Intake } from "../services/intakeService";
import type { TicketState } from "../services/ticketService";
import type { Category, Impact, Urgency } from "./ticketRules";

export const INTAKE_STATUS_LABELS: Readonly<Record<Intake["status"], string>> = {
  NEW: "Đang chờ kiểm tra thông tin",
  NEEDS_ENRICHMENT: "Cần bổ sung thông tin",
  DUPLICATE_HOLD: "Cần kiểm tra ticket trùng",
  FINAL_ASSESSMENT_QUEUED: "Đang chờ đánh giá cuối",
  ASSESSING: "Đang đánh giá",
  REVIEW_RELEASED: "Đã có đề xuất để điều phối viên duyệt",
  MANUAL_REQUIRED: "Cần điều phối viên xem xét",
  REVIEWED: "Điều phối viên đã duyệt",
  CANONICAL_FOLLOWER: "Đang theo dõi sự cố đã được liên kết",
};

export const TICKET_STATE_LABELS: Readonly<Record<TicketState, string>> = {
  INTAKE: "Đang tiếp nhận",
  FOLLOWING: "Theo ticket gốc",
  AWAITING_REVIEW: "Chờ duyệt",
  ASSIGNED: "Đã giao",
  IN_PROGRESS: "Đang làm",
  PENDING: "Đang chờ",
  AWAITING_CONFIRMATION: "Chờ xác nhận",
  REOPENED: "Mở lại",
  CLOSED: "Đã đóng",
  UNCLASSIFIED: "Không xác định",
};

export const TICKET_STATE_TONES: Readonly<Record<TicketState, TagTone>> = {
  INTAKE: "orange",
  FOLLOWING: "gray",
  AWAITING_REVIEW: "purple",
  ASSIGNED: "gray",
  IN_PROGRESS: "blue",
  PENDING: "orange",
  AWAITING_CONFIRMATION: "turquoise",
  REOPENED: "red",
  CLOSED: "green",
  UNCLASSIFIED: "gray",
};

export const CATEGORY_LABELS: Readonly<Record<Category, string>> = {
  HARDWARE: "Phần cứng",
  NETWORK: "Mạng",
  SOFTWARE: "Phần mềm",
  ACCOUNT: "Tài khoản",
  OTHER: "Khác",
};

export const IMPACT_LABELS: Readonly<Record<Impact, string>> = {
  I1: "Một máy",
  I2: "Vài máy",
  I3: "Cả phòng",
  I4: "Nhiều phòng",
};

export const URGENCY_LABELS: Readonly<Record<Urgency, string>> = {
  U1: "Có cách tạm",
  U2: "Chờ được",
  U3: "Bị chặn",
  U4: "Rất gấp",
};

/** Tên tổ theo group Keycloak (R1); tổ do đơn vị tự thêm thì hiện nguyên tên group. */
const GROUP_LABELS: Readonly<Record<string, string>> = {
  hardware: "Kỹ thuật phần cứng",
  network: "Kỹ thuật mạng",
  software: "Hỗ trợ phần mềm",
  sysadmin: "Quản trị hệ thống",
  general: "Chung",
  // Loại Khác: điều phối viên tự chọn người
  manual: "Điều phối viên chọn",
};

export const groupLabel = (group: string) => GROUP_LABELS[group] ?? group;

export const EVIDENCE_FIELD_LABELS = { title: "Tiêu đề", description: "Mô tả" } as const;

/** Mã ngắn hiển thị cho ticket (6 ký tự cuối của id; chưa có mã tuần tự từ backend). */
export const ticketCode = (id: string) => `#${id.slice(-6)}`;
