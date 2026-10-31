import { ApiError } from "@/platform/shared/api";

const FALLBACK = "Trợ lý tạm thời không phản hồi. Bạn vẫn có thể xử lý theo quy trình hiện hành.";

const STATUS_MESSAGES: Record<number, string> = {
  401: "Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.",
  403: "Trợ lý chưa khả dụng hoặc bạn không còn quyền truy cập.",
  // Tính năng AI tắt thì backend trả 404
  404: "Trợ lý chưa khả dụng hoặc bạn không còn quyền truy cập.",
  409: "Nguồn tri thức đã thay đổi. Vui lòng gửi lại câu hỏi.",
  502: "Không thể xác minh câu trả lời. Vui lòng xử lý theo quy trình hiện hành.",
  503: "Dịch vụ AI đang bận hoặc chưa kết nối được. Hãy thử lại sau.",
  504: "Trợ lý phản hồi quá lâu. Bạn vẫn có thể xử lý theo quy trình hiện hành.",
};

/** Câu báo lỗi khi gọi AI; lỗi nào cũng nhắc người dùng vẫn xử lý thủ công được. */
export const aiErrorMessage = (error: unknown) =>
  (error instanceof ApiError && STATUS_MESSAGES[error.status]) || FALLBACK;

/** Mã yêu cầu để tra log, null khi không có. */
export const aiErrorRequestId = (error: unknown) =>
  error instanceof ApiError ? error.requestId : null;
