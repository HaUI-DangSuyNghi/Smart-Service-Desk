import { toast } from "sonner";
import { ApiError } from "@/platform/shared/api";

// Gọi thông báo qua file này thay vì import thẳng sonner, để đổi thư viện chỉ sửa một chỗ
export { toast };

/** Báo lỗi bằng toast, luôn kèm mã yêu cầu nếu có để tra log (rule 04). */
export const toastError = (message: string, error?: unknown) => {
  const requestId = error instanceof ApiError ? error.requestId : null;
  toast.error(message, {
    description: requestId ? `Mã yêu cầu: ${requestId}` : undefined,
  });
};
