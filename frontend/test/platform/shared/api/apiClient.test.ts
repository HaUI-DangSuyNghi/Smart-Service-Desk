import {
  AxiosError,
  AxiosHeaders,
  isCancel,
  type AxiosAdapter,
  type InternalAxiosRequestConfig,
} from "axios";
import { afterEach, describe, expect, it, vi } from "vitest";
import {
  apiClient,
  ApiError,
  configureHttpAuth,
  invalidateHttpSession,
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
  it("cancels a queued mutation when the session ends while reading its token", async () => {
    let finish!: (token: string) => void;
    const pending = new Promise<string>((resolve) => {
      finish = resolve;
    });
    const auth = fakeAuth({ getAccessToken: vi.fn(() => pending) });
    configureHttpAuth(auth);
    const seen = fakeAdapter({ status: 200 });
    const result = apiClient
      .post("/work", { body: "old-user-draft" })
      .catch((error: unknown) => error);
    await vi.waitFor(() => expect(auth.getAccessToken).toHaveBeenCalledOnce());
    invalidateHttpSession();
    finish("old-user-token");
    expect(isCancel(await result)).toBe(true);
    expect(seen).toHaveLength(0);
  });

  it("does not deliver a successful response from the previous session", async () => {
    configureHttpAuth(fakeAuth());
    fakeAdapter({ status: 200, data: { private: "old-user-data" } });
    const adapter = apiClient.defaults.adapter as AxiosAdapter;
    apiClient.defaults.adapter = async (config) => {
      invalidateHttpSession();
      return adapter(config);
    };
    expect(isCancel(await getMe().catch((error: unknown) => error))).toBe(true);
  });

  it("does not replay an old mutation after the authentication session changes", async () => {
    configureHttpAuth(fakeAuth());
    const next = fakeAuth({ getAccessToken: vi.fn(async () => "other-user-token") });
    const seen = fakeAdapter({ status: 401 }, { status: 200 });
    const adapter = apiClient.defaults.adapter as AxiosAdapter;
    apiClient.defaults.adapter = async (config) => {
      configureHttpAuth(next);
      return adapter(config);
    };
    await expect(apiClient.post("/work", { body: "old-user-draft" })).rejects.toMatchObject({
      status: 401,
    });
    expect(seen).toHaveLength(1);
    expect(next.onUnauthorized).not.toHaveBeenCalled();
  });

  it("discards renewal results from a previous authentication session", async () => {
    let finish!: (token: string) => void;
    const pending = new Promise<string>((resolve) => {
      finish = resolve;
    });
    const previous = fakeAuth({ renewAccessToken: vi.fn(() => pending) });
    configureHttpAuth(previous);
    const seen = fakeAdapter({ status: 401 }, { status: 200 });
    const rejected = expect(
      apiClient.post("/work", { body: "old-user-draft" }),
    ).rejects.toMatchObject({ status: 401 });
    await vi.waitFor(() => expect(previous.renewAccessToken).toHaveBeenCalledOnce());
    const next = fakeAuth({ getAccessToken: vi.fn(async () => "other-user-token") });
    configureHttpAuth(next);
    finish("renewed-old-user-token");
    await rejected;
    expect(seen).toHaveLength(1);
    expect(next.onUnauthorized).not.toHaveBeenCalled();
  });

  it("retries a late 401 with the current token without renewing again", async () => {
    const auth = fakeAuth({
      getAccessToken: vi.fn().mockResolvedValueOnce("old-token").mockResolvedValue("new-token"),
    });
    configureHttpAuth(auth);
    const seen = fakeAdapter({ status: 401 }, { status: 200 });
    await getMe();
    expect(authHeader(seen[1])).toBe("Bearer new-token");
    expect(auth.renewAccessToken).not.toHaveBeenCalled();
  });

  it("shares renewal across concurrent unauthorized requests", async () => {
    let token = "old-token";
    let finishRenewal!: (token: string) => void;
    const pendingRenewal = new Promise<string>((resolve) => {
      finishRenewal = resolve;
    });
    const auth = fakeAuth({
      getAccessToken: vi.fn(async () => token),
      renewAccessToken: vi.fn(() => pendingRenewal),
    });
    configureHttpAuth(auth);
    fakeAdapter({ status: 401 }, { status: 401 }, { status: 200 }, { status: 200 });
    const requests = Promise.all([getMe(), getMe()]);
    await vi.waitFor(() => expect(auth.renewAccessToken).toHaveBeenCalled());
    const renewalCount = vi.mocked(auth.renewAccessToken).mock.calls.length;
    token = "new-token";
    finishRenewal(token);
    await requests;
    expect(renewalCount).toBe(1);
  });

  it("keeps the API error and signs in when renewal rejects", async () => {
    const auth = fakeAuth({
      renewAccessToken: vi.fn(async () => {
        throw new Error("offline");
      }),
    });
    configureHttpAuth(auth);
    fakeAdapter({ status: 401 });
    await expect(getMe()).rejects.toMatchObject({ status: 401 });
    expect(auth.onUnauthorized).toHaveBeenCalledOnce();
  });

  it("does not reuse a stale Authorization header when the session is gone", async () => {
    configureHttpAuth(fakeAuth({ getAccessToken: vi.fn(async () => null) }));
    const seen = fakeAdapter({ status: 200 });
    await apiClient.get("/core/v1/me", { headers: { Authorization: "Bearer stale-token" } });
    expect(authHeader(seen[0])).toBeUndefined();
  });

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
