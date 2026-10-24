import { ApiError } from "@/platform/shared/api";

const statusOf = (error: unknown) => (error instanceof ApiError ? error.status : null);

/** Câu báo lỗi khi nhận việc, theo luật backend (409 thua tranh, 403 nguyên tắc 4 mắt). */
export const claimErrorMessage = (error: unknown) => {
  switch (statusOf(error)) {
    case 409:
      return "Việc này vừa có người khác nhận.";
    case 403:
      return "Bạn không nhận được việc này: yêu cầu do chính bạn tạo hoặc bạn không thuộc nhóm xử lý.";
    case 404:
      return "Việc không còn tồn tại.";
    default:
      return "Không nhận được việc.";
  }
};

/** Câu báo lỗi khi hoàn thành bước. */
export const completeErrorMessage = (error: unknown) => {
  switch (statusOf(error)) {
    case 409:
      return "Việc đã thay đổi hoặc đã được hoàn thành.";
    case 403:
      return "Bạn không còn giữ việc này.";
    case 400:
      return "Kết quả hoặc ghi chú không hợp lệ.";
    default:
      return "Không hoàn thành được bước.";
  }
};

/** Câu báo lỗi khi giao việc. */
export const assignErrorMessage = (error: unknown) => {
  switch (statusOf(error)) {
    case 400:
      return "Người này không thuộc nhóm xử lý của bước.";
    case 403:
      return "Không giao được: người này là người tạo yêu cầu, hoặc bạn không có quyền giao việc.";
    case 409:
      return "Việc đã thay đổi hoặc đã hoàn thành.";
    case 503:
      return "Danh bạ tạm thời không phản hồi, thử lại sau.";
    default:
      return "Không giao được việc.";
  }
};

/** Việc đã xong hoặc không còn (409/404): đóng dialog vì chọn lại cũng không được. */
export const isTaskGoneError = (error: unknown) => {
  const status = statusOf(error);
  return status === 409 || status === 404;
};

/** Lỗi do trạng thái việc đã đổi ở server: dialog nên đóng vì gửi lại cũng không được. */
export const isStaleTaskError = (error: unknown) => {
  const status = statusOf(error);
  return status === 409 || status === 403 || status === 404;
};
