import { AxiosHeaders, isAxiosError, type RawAxiosHeaders } from "axios";

/** Body lỗi chuẩn RFC 9457 mà backend trả về (xem Problem trong contracts/openapi/core.yaml). */
interface ProblemBody {
  title?: string;
  detail?: string;
  code?: string;
  requestId?: string;
}

/** Lỗi khi gọi API, giữ lại requestId để người dùng báo lỗi và tra log. status = 0 là lỗi mạng. */
export class ApiError extends Error {
  readonly status: number;
  readonly code: string | null;
  readonly title: string;
  readonly detail: string | null;
  readonly requestId: string | null;

  constructor(status: number, problem: ProblemBody, requestId: string | null) {
    super(problem.detail ?? problem.title ?? `Request failed (${status})`);
    this.name = "ApiError";
    this.status = status;
    this.code = problem.code ?? null;
    this.title = problem.title ?? `HTTP ${status}`;
    this.detail = problem.detail ?? null;
    this.requestId = problem.requestId ?? requestId;
  }

  static from(error: unknown): ApiError {
    if (error instanceof ApiError) return error;
    if (!isAxiosError(error) || !error.response) {
      return new ApiError(0, { title: "Không kết nối được máy chủ" }, null);
    }
    const { status, data, headers } = error.response;
    // Gateway hoặc proxy có thể trả lỗi không phải JSON
    const problem = typeof data === "object" && data !== null ? (data as ProblemBody) : {};
    // Tên header không phân biệt hoa thường (gateway gửi X-Request-ID)
    const requestId = AxiosHeaders.from(headers as RawAxiosHeaders).get("x-request-id");
    return new ApiError(status, problem, typeof requestId === "string" ? requestId : null);
  }
}
