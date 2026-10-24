import { describe, expect, it } from "vitest";
import {
  createAuthSettings,
  readAuthEnv,
  safeReturnTo,
} from "../../../../src/platform/human/auth/config";

const env = { authority: "http://localhost:8081/realms/dx-lab", clientId: "dx-web" };

describe("readAuthEnv", () => {
  it("đọc authority và client id", () => {
    expect(
      readAuthEnv({ VITE_OIDC_AUTHORITY: env.authority, VITE_OIDC_CLIENT_ID: env.clientId }),
    ).toEqual(env);
  });

  it("báo lỗi khi thiếu biến", () => {
    expect(() => readAuthEnv({ VITE_OIDC_AUTHORITY: env.authority })).toThrow(/\.env\.example/);
  });
});

describe("createAuthSettings", () => {
  it("dùng Authorization Code, scope openid và đường dẫn callback theo origin", () => {
    const settings = createAuthSettings(env, "http://localhost:5173");

    expect(settings).toMatchObject({
      authority: env.authority,
      client_id: "dx-web",
      response_type: "code",
      scope: "openid",
      redirect_uri: "http://localhost:5173/auth/callback",
      silent_redirect_uri: "http://localhost:5173/auth/silent",
      post_logout_redirect_uri: "http://localhost:5173/",
    });
  });
});

describe("safeReturnTo", () => {
  it("giữ đường dẫn nội bộ", () => {
    expect(safeReturnTo("/servicedesk/tickets?q=wifi")).toBe("/servicedesk/tickets?q=wifi");
  });

  it("chặn URL ngoài, giá trị lạ và trang auth", () => {
    for (const value of [
      "https://evil.example",
      "//evil.example",
      undefined,
      42,
      "/auth/callback",
    ]) {
      expect(safeReturnTo(value)).toBe("/home");
    }
  });
});
