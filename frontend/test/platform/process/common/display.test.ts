import { describe, expect, it } from "vitest";
import {
  groupRole,
  outcomeTone,
  personName,
  shortKey,
} from "../../../../src/platform/process/common/display";

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

describe("personName", () => {
  const names = new Map([["sub-1", "Demo Agent"]]);

  it("lấy họ tên theo danh bạ", () => {
    expect(personName(names, "sub-1")).toBe("Demo Agent");
  });

  it("không có trong danh bạ thì hiện 8 ký tự đầu của subject", () => {
    expect(personName(names, "0f1e2d3c-aaaa-bbbb")).toBe("0f1e2d3c");
  });
});

describe("shortKey", () => {
  it("rút gọn mã dài, giữ nguyên mã ngắn", () => {
    expect(shortKey("4b8c3a2e-1111-2222-3333-444455556666")).toBe("4b8c3a2e");
    expect(shortKey("T-0042")).toBe("T-0042");
  });
});
