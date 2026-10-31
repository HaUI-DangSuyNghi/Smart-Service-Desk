import { describe, expect, it } from "vitest";
import { slaStatus } from "../../../../src/platform/process/work/slaFormat";
import type { Sla } from "../../../../src/platform/services";

const NOW = new Date("2026-10-26T08:00:00Z").getTime();

const sla = (overrides: Partial<Sla> = {}): Sla => ({
  startBy: "2026-10-26T09:00:00Z",
  resolveBy: "2026-10-26T16:00:00Z",
  startBreached: false,
  resolveBreached: false,
  warning: false,
  clockPaused: false,
  ...overrides,
});

describe("slaStatus", () => {
  it("chưa bắt đầu thì đếm tới hạn bắt đầu", () => {
    expect(slaStatus(sla(), false, NOW)).toEqual({
      text: "Bắt đầu trong 1 giờ",
      tone: "gray",
      deadline: "2026-10-26T09:00:00Z",
    });
  });

  it("đã bắt đầu thì đếm tới hạn xong", () => {
    expect(slaStatus(sla(), true, NOW).text).toBe("Còn 8 giờ");
  });

  it("sắp hết hạn thì màu cam", () => {
    expect(slaStatus(sla({ warning: true }), true, NOW).tone).toBe("orange");
  });

  it("quá hạn xử lý thì đỏ, kể cả đã bắt đầu", () => {
    expect(slaStatus(sla({ resolveBreached: true }), true, NOW)).toMatchObject({
      text: "Quá hạn xử lý",
      tone: "red",
    });
  });

  it("quá hạn bắt đầu chỉ tính khi chưa bắt đầu", () => {
    expect(slaStatus(sla({ startBreached: true }), false, NOW).text).toBe("Quá hạn bắt đầu");
    expect(slaStatus(sla({ startBreached: true }), true, NOW).text).toBe("Còn 8 giờ");
  });

  it("chờ người dùng trả lời thì báo tạm dừng tính hạn", () => {
    expect(slaStatus(sla({ clockPaused: true }), true, NOW)).toMatchObject({
      text: "Tạm dừng tính hạn",
      tone: "gray",
    });
  });
});
