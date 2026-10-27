import type { AlertSeverity, AlertType } from "@/platform/services";
import type { TagTone } from "@/platform/shared/ui/Tag";

export const ALERT_LABELS: Readonly<Record<AlertType, string>> = {
  START_OVERDUE: "Quá hạn bắt đầu",
  SLA_WARNING: "Sắp hết hạn xử lý",
  SLA_BREACHED: "Quá hạn xử lý",
  PENDING_TOO_LONG: "Đang chờ quá lâu",
  CRITICAL_NEW: "Yêu cầu khẩn cấp mới",
  CRITICAL_AUTO_QUEUED: "Khẩn cấp tự vào hàng đợi",
  REOPENED: "Người yêu cầu báo chưa xong",
  REOPEN_UNCLAIMED: "Việc mở lại chưa được nhận",
  ASSIGNEE_OFF_DUTY: "Người giữ việc đã nghỉ",
  NO_ONE_ON_DUTY: "Tổ không ai trực",
  ESTIMATE_REQUESTED: "Có yêu cầu sửa ước tính",
};

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
