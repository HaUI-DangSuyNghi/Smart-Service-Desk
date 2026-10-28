import type { Alert, AlertSeverity, AlertType } from "@/platform/services";
import type { TagTone } from "@/platform/shared/ui/Tag";

export const ALERT_LABELS: Readonly<Record<AlertType, string>> = {
  START_OVERDUE: "Quá hạn bắt đầu",
  SLA_WARNING: "Sắp hết hạn xử lý",
  SLA_BREACHED: "Quá hạn xử lý",
  PENDING_TOO_LONG: "Đang chờ quá lâu",
  CRITICAL_NEW: "Yêu cầu khẩn cấp mới",
  CLASSIFICATION_CHANGED: "Phân loại đã được sửa",
  REOPENED: "Người yêu cầu báo chưa xong",
  REOPEN_UNCLAIMED: "Việc mở lại chưa được nhận",
  ASSIGNEE_OFF_DUTY: "Người giữ việc đã nghỉ",
  NO_ONE_ON_DUTY: "Tổ không ai trực",
  CRITICAL_UNREVIEWED: "Khẩn cấp chưa ai duyệt",
};

/** Backend có thể thêm loại mới trước FE: chưa có nhãn thì hiện mã. */
export const alertLabel = (type: string) => ALERT_LABELS[type as AlertType] ?? type;

/** Việc bị cảnh báo; cảnh báo SLA có label trùng mã loại nên hiện mã việc (8 ký tự đầu businessKey). */
export const alertSubject = (alert: Pick<Alert, "type" | "label" | "businessKey">) =>
  alert.label && alert.label !== alert.type
    ? alert.label
    : `#${alert.businessKey.slice(0, 8).toUpperCase()}`;

export const SEVERITY_TONES: Readonly<Record<AlertSeverity, TagTone>> = {
  INFO: "blue",
  WARNING: "orange",
  CRITICAL: "red",
};

export const SEVERITY_LABELS: Readonly<Record<AlertSeverity, string>> = {
  INFO: "Thông tin",
  WARNING: "Cảnh báo",
  CRITICAL: "Khẩn cấp",
};
