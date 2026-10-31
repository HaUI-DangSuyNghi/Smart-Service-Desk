import { describe, expect, it } from "vitest";
import {
  assignErrorMessage,
  claimErrorMessage,
  completeErrorMessage,
  isStaleTaskError,
  isTaskGoneError,
} from "../../../../src/platform/process/tasks/taskErrors";
import { ApiError } from "../../../../src/platform/shared/api";

const apiError = (status: number) => new ApiError(status, { title: "Lỗi" }, "req-1");

describe("taskErrors", () => {
  it("nhận việc thua tranh báo đã có người nhận", () => {
    expect(claimErrorMessage(apiError(409))).toBe("Việc này vừa có người khác nhận.");
  });

  it("lỗi không phải ApiError dùng câu chung", () => {
    expect(claimErrorMessage(new Error("boom"))).toBe("Không nhận được việc.");
    expect(completeErrorMessage(new Error("boom"))).toBe("Không hoàn thành được bước.");
  });

  it("giao sai người giữ dialog mở, việc đã xong thì đóng", () => {
    expect(assignErrorMessage(apiError(400))).toBe("Người này không thuộc nhóm xử lý của bước.");
    expect(isTaskGoneError(apiError(400))).toBe(false);
    expect(isTaskGoneError(apiError(403))).toBe(false);
    expect(isTaskGoneError(apiError(409))).toBe(true);
  });

  it("409/403/404 là việc đã đổi ở server, nên đóng dialog", () => {
    expect(isStaleTaskError(apiError(409))).toBe(true);
    expect(isStaleTaskError(apiError(403))).toBe(true);
    expect(isStaleTaskError(apiError(400))).toBe(false);
  });
});
