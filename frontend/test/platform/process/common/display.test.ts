import { describe, expect, it } from "vitest";
import { groupRole, outcomeTone, shortKey } from "../../../../src/platform/process/common/display";

describe("outcomeTone", () => {
  it("tô màu theo nghĩa của kết quả", () => {
    expect(outcomeTone("approved")).toBe("green");
    expect(outcomeTone("rejected")).toBe("red");
    expect(outcomeTone("rework")).toBe("orange");
  });

  it("kết quả lạ hiện xám", () => {
    expect(outcomeTone("escalated")).toBe("gray");
  });
});

describe("groupRole", () => {
  it("bỏ phần workspace của candidate group", () => {
    expect(groupRole("lab-a:agent")).toBe("agent");
  });
});

describe("shortKey", () => {
  it("rút gọn mã dài, giữ nguyên mã ngắn", () => {
    expect(shortKey("4b8c3a2e-1111-2222-3333-444455556666")).toBe("4b8c3a2e");
    expect(shortKey("T-0042")).toBe("T-0042");
  });
});
