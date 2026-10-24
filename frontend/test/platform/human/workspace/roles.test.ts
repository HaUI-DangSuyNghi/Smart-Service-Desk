import { describe, expect, it } from "vitest";
import { hasAnyRole, roleLabel, roleTone } from "../../../../src/platform/human/workspace/roles";

describe("hasAnyRole", () => {
  it("không yêu cầu vai trò thì luôn cho qua", () => {
    expect(hasAnyRole([], undefined)).toBe(true);
    expect(hasAnyRole([], [])).toBe(true);
  });

  it("chỉ cần có một trong các vai trò yêu cầu", () => {
    expect(hasAnyRole(["requester", "agent"], ["agent", "coordinator"])).toBe(true);
    expect(hasAnyRole(["requester"], ["agent", "coordinator"])).toBe(false);
  });
});

describe("roleLabel / roleTone", () => {
  it("dịch vai trò mặc định, giữ nguyên vai trò lạ", () => {
    expect(roleLabel("agent")).toBe("Kỹ thuật viên");
    expect(roleLabel("auditor")).toBe("auditor");
    expect(roleTone("auditor")).toBe("gray");
  });
});
