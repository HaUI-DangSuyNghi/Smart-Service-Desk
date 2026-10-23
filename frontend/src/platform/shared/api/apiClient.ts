import axios, { type InternalAxiosRequestConfig } from "axios";
import { ApiError } from "./ApiError";

/** Cách client lấy token. Khối auth cung cấp lúc khởi động app, shared/api không phụ thuộc auth. */
export interface HttpAuth {
  getAccessToken: () => Promise<string | null>;
  /** Gia hạn token; trả null nếu không gia hạn được. */
  renewAccessToken: () => Promise<string | null>;
  /** Gọi khi đã gia hạn mà vẫn 401: chuyển người dùng sang đăng nhập. */
  onUnauthorized: () => void;
}

interface RetryableConfig extends InternalAxiosRequestConfig {
  retriedAfterRenew?: boolean;
}

let auth: HttpAuth | null = null;

export const configureHttpAuth = (value: HttpAuth) => {
  auth = value;
};

/** Axios instance duy nhất của app. Mọi service gọi API qua đây. */
export const apiClient = axios.create({
  // Đi qua proxy Vite → APISIX; service chỉ viết phần sau /api
  baseURL: "/api",
  // Chặn gọi URL tuyệt đối vượt qua gateway
  allowAbsoluteUrls: false,
  headers: { Accept: "application/json" },
});

apiClient.interceptors.request.use(async (config) => {
  const token = await auth?.getAccessToken();
  if (token) config.headers.set("Authorization", `Bearer ${token}`);
  return config;
});

apiClient.interceptors.response.use(
  (response) => response,
  async (error: unknown) => {
    const apiError = ApiError.from(error);
    const config = (error as { config?: RetryableConfig }).config;

    // Token có thể vừa hết hạn: gia hạn 1 lần rồi gọi lại
    if (apiError.status === 401 && auth && config && !config.retriedAfterRenew) {
      config.retriedAfterRenew = true;
      if (await auth.renewAccessToken()) return apiClient(config);
    }
    if (apiError.status === 401) auth?.onUnauthorized();
    throw apiError;
  },
);
