import { ApiError } from "@/platform/shared/api";

// Thông điệp 409 backend gửi (tiếng Anh) → câu cho người dùng; khớp theo đoạn đầu của detail
const CONFLICT_MESSAGES: readonly [string, string][] = [
  ["Active work limit reached", "Bạn đang có việc Đang làm. Tạm dừng việc đó trước khi bắt đầu việc mới."],
  ["Start or resume work before completion", "Bắt đầu hoặc tiếp tục việc trước khi báo xong."],
  ["Pause active work before reassignment", "Việc đang làm: người giữ việc phải tạm dừng trước khi giao lại."],
  ["Agent is off duty", "Người được chọn đang nghỉ trực."],
  ["Task already claimed", "Việc đã có người nhận."],
  ["Unexpected task key", "Việc đã sang bước khác, tải lại để xem trạng thái mới."],
  ["already decided", "Yêu cầu này đã được quyết."],
];

/**
 * Câu báo lỗi khi thao tác trên việc. 409 có nhiều lý do (đã có việc Đang làm, việc đã đổi,
 * yêu cầu đã được quyết) nên dịch theo câu backend gửi kèm.
 */
export const workErrorMessage = (error: unknown, fallback: string) => {
  if (!(error instanceof ApiError)) return fallback;
  switch (error.status) {
    case 409: {
      const known = CONFLICT_MESSAGES.find(([key]) => error.detail?.includes(key));
      return known?.[1] ?? "Việc đã thay đổi, tải lại để xem trạng thái mới.";
    }
    case 403:
      return "Bạn không còn giữ việc này.";
    case 404:
      return "Việc không còn tồn tại.";
    case 400:
      return "Thông tin gửi lên chưa hợp lệ.";
    default:
      return fallback;
  }
};
