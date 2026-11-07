import axios, { CanceledError, isCancel, type InternalAxiosRequestConfig } from "axios";
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
  sessionRevision?: number;
}

let auth: HttpAuth | null = null;
let renewal: Promise<string | null> | null = null;
let sessionRevision = 0;

export const invalidateHttpSession = () => {
  sessionRevision++;
  renewal = null;
};

export const configureHttpAuth = (value: HttpAuth) => {
  invalidateHttpSession();
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
  const scoped = config as RetryableConfig;
  scoped.sessionRevision ??= sessionRevision;
  const token = await auth?.getAccessToken();
  if (scoped.sessionRevision !== sessionRevision)
    throw new CanceledError("Authentication session changed", config);
  if (token) config.headers.set("Authorization", `Bearer ${token}`);
  else config.headers.delete("Authorization");
  return config;
});

apiClient.interceptors.response.use(
  (response) => {
    if ((response.config as RetryableConfig).sessionRevision !== sessionRevision)
      throw new CanceledError("Authentication session changed", response.config);
    return response;
  },
  async (error: unknown) => {
    if (isCancel(error)) throw error;
    const apiError = ApiError.from(error);
    const config = (error as { config?: RetryableConfig } | null)?.config;
    const revision = sessionRevision;
    if (config && config.sessionRevision !== revision) throw apiError;

    // Token có thể vừa hết hạn: gia hạn 1 lần rồi gọi lại
    if (apiError.status === 401 && auth && config && !config.retriedAfterRenew) {
      config.retriedAfterRenew = true;
      const token = await auth.getAccessToken();
      if (revision !== sessionRevision) throw apiError;
      if (token && config.headers.get("Authorization") !== `Bearer ${token}`)
        return apiClient(config);
      renewal ??= auth
        .renewAccessToken()
        .catch(() => null)
        .finally(() => {
          if (revision === sessionRevision) renewal = null;
        });
      const renewed = await renewal;
      if (revision !== sessionRevision) throw apiError;
      if (renewed) return apiClient(config);
    }
    if (apiError.status === 401) auth?.onUnauthorized();
    throw apiError;
  },
);
