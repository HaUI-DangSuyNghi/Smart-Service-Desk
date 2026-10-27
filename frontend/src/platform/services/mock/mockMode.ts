import { ApiError } from "@/platform/shared/api";

/**
 * Bật dữ liệu giả cho phần nghiệp vụ backend chưa cài (contracts/hpdi/openapi-target.yaml):
 * đặt VITE_WORKFLOW_MOCK=true trong .env.local. Backend cài xong thì xóa thư mục mock/ và cờ này.
 */
export const isWorkflowMock = import.meta.env.VITE_WORKFLOW_MOCK === "true";

/**
 * Có các màn hàng đợi, ca trực, cảnh báo, cấu hình hay chưa.
 * Hiện chỉ có khi bật dữ liệu giả; backend cài xong thì đổi thành true.
 */
export const isWorkflowAvailable = isWorkflowMock;

// Trễ giả để thấy skeleton và nút "Đang gửi..." như khi gọi API thật
const MOCK_DELAY_MS = 300;

export const mockDelay = () => new Promise<void>((resolve) => setTimeout(resolve, MOCK_DELAY_MS));

/** Lỗi giả cùng dạng ApiError để giao diện xử lý như lỗi thật. */
export const mockError = (status: number, title: string) =>
  new ApiError(status, { title, detail: title }, `mock-${Date.now()}`);
