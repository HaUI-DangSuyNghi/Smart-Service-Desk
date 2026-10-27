import { ApiError } from "@/platform/shared/api";

/**
 * Câu báo lỗi khi thao tác trên việc. 409 có nhiều lý do (đã có việc Đang làm, việc đã đổi,
 * yêu cầu đã được quyết) nên ưu tiên câu backend gửi kèm.
 */
export const workErrorMessage = (error: unknown, fallback: string) => {
  if (!(error instanceof ApiError)) return fallback;
  switch (error.status) {
    case 409:
      return error.detail ?? "Việc đã thay đổi, tải lại để xem trạng thái mới.";
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
