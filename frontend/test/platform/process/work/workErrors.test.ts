import { describe, expect, it } from "vitest";
import { workErrorMessage } from "../../../../src/platform/process/work/workErrors";
import { ApiError } from "../../../../src/platform/shared/api";

const conflict = (detail: string) => new ApiError(409, { detail }, "req-1");

describe("workErrorMessage", () => {
  it("dịch thông điệp 409 của backend", () => {
    expect(workErrorMessage(conflict("Active work limit reached"), "x")).toContain("Tạm dừng");
    expect(workErrorMessage(conflict("Agent is off duty"), "x")).toBe("Người được chọn đang nghỉ trực.");
  });

  it("409 lạ thì báo tải lại, lỗi không phải ApiError thì dùng câu mặc định", () => {
    expect(workErrorMessage(conflict("Something new"), "x")).toContain("tải lại");
    expect(workErrorMessage(new Error("boom"), "Không bắt đầu được.")).toBe("Không bắt đầu được.");
  });
});
