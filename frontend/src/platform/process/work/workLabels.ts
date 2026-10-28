import type {
  AssignmentSuggestion,
  Estimate,
  PendingReason,
  Priority,
  ReleaseReason,
  WorkState,
} from "@/platform/services";
import { formatSeconds } from "@/platform/shared/lib/format";
import type { TagTone } from "@/platform/shared/ui/Tag";

export const PRIORITY_LABELS: Readonly<Record<Priority, string>> = {
  LOW: "Thấp",
  MEDIUM: "Trung bình",
  HIGH: "Cao",
  CRITICAL: "Khẩn cấp",
};

export const PRIORITY_TONES: Readonly<Record<Priority, TagTone>> = {
  LOW: "gray",
  MEDIUM: "blue",
  HIGH: "orange",
  CRITICAL: "red",
};

export const WORK_STATE_LABELS: Readonly<Record<WorkState, string>> = {
  NOT_STARTED: "Chưa bắt đầu",
  IN_PROGRESS: "Đang làm",
  PENDING: "Đang chờ",
};

export const WORK_STATE_TONES: Readonly<Record<WorkState, TagTone>> = {
  NOT_STARTED: "gray",
  IN_PROGRESS: "blue",
  PENDING: "orange",
};

export const PENDING_REASON_LABELS: Readonly<Record<PendingReason, string>> = {
  WAITING_REQUESTER: "Chờ người yêu cầu trả lời",
  WAITING_PARTS: "Chờ linh kiện",
  WAITING_EXTERNAL: "Chờ bên ngoài",
  OTHER: "Lý do khác",
};

export const RELEASE_REASON_LABELS: Readonly<Record<ReleaseReason, string>> = {
  WRONG_GROUP: "Sai tổ xử lý",
  HANDOVER: "Hết ca, bàn giao",
  OTHER: "Lý do khác",
};

/** Lựa chọn "để chờ ca sau" (không giao ai) khi tổ không có người trực (R3). */
export const WAIT_FOR_SHIFT = "";

/** 90 → "1 giờ 30 phút". */
export const formatMinutes = (minutes: number) => formatSeconds(minutes * 60);

/** Nguồn của ET để người duyệt biết độ tin cậy (R2). */
export const estimateBasisText = (estimate: Estimate) => {
  switch (estimate.basis) {
    case "COORDINATOR":
      return "Điều phối viên đặt";
    case "SIMILAR":
      return `Trung vị ${estimate.sampleSize ?? "?"} việc tương tự`;
    default:
      return "Mặc định theo loại";
  }
};

/** Giải thích đề xuất người xử lý; backend trả câu kỹ thuật nên dịch theo từng nhánh (R2). */
export const suggestionReasonText = (
  suggestion: Pick<AssignmentSuggestion, "group" | "recommended" | "reason">,
) => {
  const base =
    suggestion.group === "manual"
      ? "Loại Khác: điều phối viên tự chọn người xử lý."
      : suggestion.recommended === null
        ? "Tổ không còn ai đang trực: chọn người khác hoặc để chờ ca sau."
        : "Đề xuất người ít tải nhất (chênh dưới 15 phút thì xét ít việc nhất hôm nay).";
  return suggestion.reason.includes("ESTIMATE_EXCEEDS_SHIFT")
    ? `${base} Ước tính dài hơn thời gian còn lại của ca với một số người.`
    : base;
};

/** Được nâng bậc ưu tiên do chờ lâu (R5). */
export const isEscalated = (item: { priority: Priority; basePriority: Priority }) =>
  item.priority !== item.basePriority;
