import {
  AxiosError,
  AxiosHeaders,
  type AxiosAdapter,
  type InternalAxiosRequestConfig,
} from "axios";
import { afterEach, describe, expect, it, vi } from "vitest";
import {
  apiClient,
  ApiError,
  configureHttpAuth,
  type HttpAuth,
} from "../../../../src/platform/shared/api";
import { getMe } from "../../../../src/platform/services";

interface FakeResponse {
  status: number;
  data?: unknown;
  headers?: Record<string, string>;
}

/** Adapter giả: trả lần lượt các response, status >= 400 thì báo lỗi như axios thật. */
const fakeAdapter = (...responses: FakeResponse[]) => {
  const seen: InternalAxiosRequestConfig[] = [];
  const adapter: AxiosAdapter = async (config) => {
    seen.push(config);
    const next = responses.shift() ?? { status: 500 };
    const response = {
      status: next.status,
      statusText: "",
      data: next.data,
      headers: new AxiosHeaders(next.headers ?? {}),
      config,
    };
    if (next.status >= 400) throw new AxiosError("fail", undefined, config, null, response);
    return response;
  };
  apiClient.defaults.adapter = adapter;
  return seen;
};

const fakeAuth = (overrides: Partial<HttpAuth> = {}): HttpAuth => {
  return {
    getAccessToken: vi.fn(async () => "old-token"),
    renewAccessToken: vi.fn(async () => "new-token"),
    onUnauthorized: vi.fn(),
    ...overrides,
  };
};

const authHeader = (config: InternalAxiosRequestConfig | undefined) => {
  return config?.headers.get("Authorization");
};

afterEach(() => {
  apiClient.defaults.adapter = undefined;
});

describe("apiClient", () => {
  it("gắn Bearer token, gọi qua /api và trả data", async () => {
    configureHttpAuth(fakeAuth());
    const seen = fakeAdapter({ status: 200, data: { username: "user1" } });

    await expect(getMe()).resolves.toEqual({ username: "user1" });
    expect(seen[0]?.baseURL).toBe("/api");
    expect(seen[0]?.url).toBe("/core/v1/me");
    expect(authHeader(seen[0])).toBe("Bearer old-token");
  });

  it("401 thì gia hạn token một lần rồi gọi lại", async () => {
    let token = "old-token";
    const auth = fakeAuth({
      getAccessToken: vi.fn(async () => token),
      renewAccessToken: vi.fn(async () => (token = "new-token")),
    });
    configureHttpAuth(auth);
    const seen = fakeAdapter({ status: 401 }, { status: 200, data: { ok: true } });

    await expect(getMe()).resolves.toEqual({ ok: true });
    expect(authHeader(seen[1])).toBe("Bearer new-token");
    expect(auth.onUnauthorized).not.toHaveBeenCalled();
  });

  it("gia hạn xong vẫn 401 thì chuyển sang đăng nhập", async () => {
    const auth = fakeAuth();
    configureHttpAuth(auth);
    fakeAdapter({ status: 401 }, { status: 401, data: { code: "UNAUTHENTICATED" } });

    await expect(getMe()).rejects.toBeInstanceOf(ApiError);
    expect(auth.onUnauthorized).toHaveBeenCalledOnce();
  });

  it("chuyển Problem JSON thành ApiError giữ requestId", async () => {
    configureHttpAuth(fakeAuth());
    fakeAdapter({
      status: 403,
      data: { title: "Forbidden", code: "FORBIDDEN", detail: "Workspace access denied" },
      headers: { "X-Request-ID": "req-123" },
    });

    const error = await getMe().catch((e: unknown) => e);
    expect(error).toMatchObject({
      status: 403,
      code: "FORBIDDEN",
      detail: "Workspace access denied",
      requestId: "req-123",
    });
  });

  it("lỗi mạng thành ApiError status 0", async () => {
    configureHttpAuth(fakeAuth());
    apiClient.defaults.adapter = async (config) => {
      throw new AxiosError("Network Error", "ERR_NETWORK", config);
    };

    await expect(getMe()).rejects.toMatchObject({ status: 0 });
  });
});
