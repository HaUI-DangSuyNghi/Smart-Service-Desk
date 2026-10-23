import { IconAlertTriangle } from "@tabler/icons-react";
import { ApiError } from "@/platform/shared/api";

interface Props {
  error: unknown;
  /** Câu hiển thị khi lỗi không phải ApiError hoặc không có chi tiết. */
  fallback?: string;
}

const STATUS_MESSAGES: Record<number, string> = {
  0: "Không kết nối được máy chủ.",
  403: "Bạn không có quyền xem nội dung này.",
  404: "Không tìm thấy dữ liệu, hoặc bạn không có quyền xem.",
  503: "Dịch vụ tạm thời không phản hồi, vui lòng thử lại sau.",
};

/** Báo lỗi gọi API, luôn kèm requestId để tra log. */
export const ErrorState = ({ error, fallback = "Đã xảy ra lỗi." }: Props) => {
  const apiError = error instanceof ApiError ? error : null;
  const message = (apiError && STATUS_MESSAGES[apiError.status]) ?? apiError?.detail ?? fallback;

  return (
    <div role="alert" className="flex flex-col items-center gap-2 px-4 py-12 text-center">
      <IconAlertTriangle size={20} stroke={2} className="text-crm-danger" />
      <p className="text-crm-text">{message}</p>
      {apiError?.requestId && (
        <p className="text-crm-sm text-crm-text-3">Mã yêu cầu: {apiError.requestId}</p>
      )}
    </div>
  );
};
