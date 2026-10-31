import { describe, expect, it } from "vitest";
import {
  hasAnyPermission,
  PERMISSIONS,
} from "../../../../src/platform/human/workspace/permissions";

describe("hasAnyPermission", () => {
  const requester = new Set([PERMISSIONS.requestCreate, PERMISSIONS.auditViewOwn]);

  it("không yêu cầu quyền thì luôn cho qua", () => {
    expect(hasAnyPermission(new Set(), undefined)).toBe(true);
    expect(hasAnyPermission(new Set(), [])).toBe(true);
  });

  it("người yêu cầu được tạo yêu cầu nhưng không vào được công việc, tài liệu, thành viên", () => {
    expect(hasAnyPermission(requester, [PERMISSIONS.requestCreate])).toBe(true);
    expect(hasAnyPermission(requester, [PERMISSIONS.taskHandle])).toBe(false);
    expect(hasAnyPermission(requester, [PERMISSIONS.documentView])).toBe(false);
    expect(hasAnyPermission(requester, [PERMISSIONS.memberView])).toBe(false);
  });

  it("chỉ cần một trong các quyền", () => {
    expect(hasAnyPermission(requester, [PERMISSIONS.memberView, PERMISSIONS.requestCreate])).toBe(
      true,
    );
  });
});
