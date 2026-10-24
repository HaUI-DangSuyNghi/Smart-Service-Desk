import { describe, expect, it } from "vitest";
import {
  formatDuration,
  formatRelativeTime,
  formatSeconds,
} from "../../../../src/platform/shared/lib/format";

const NOW = new Date("2026-10-24T10:00:00Z").getTime();
const ago = (seconds: number) => new Date(NOW - seconds * 1000).toISOString();

describe("formatRelativeTime", () => {
  it("dưới 1 phút hiện vừa xong", () => {
    expect(formatRelativeTime(ago(30), NOW)).toBe("vừa xong");
  });

  it("hiện phút, giờ, ngày trước", () => {
    expect(formatRelativeTime(ago(5 * 60), NOW)).toBe("5 phút trước");
    expect(formatRelativeTime(ago(3 * 3600), NOW)).toBe("3 giờ trước");
    // 1–2 ngày tiếng Việt có chữ riêng ("Hôm qua", "Hôm kia"), nên thử với 3 ngày
    expect(formatRelativeTime(ago(3 * 86400), NOW)).toBe("3 ngày trước");
  });

  it("cũ hơn 7 ngày thì hiện ngày tháng", () => {
    // Ngày giờ dạng ngắn của tiếng Việt in năm 2 chữ số
    expect(formatRelativeTime(ago(8 * 86400), NOW)).toMatch(/16\/10\/26/);
  });
});

describe("formatDuration", () => {
  const start = "2026-10-24T08:00:00Z";

  it("dưới 1 phút", () => {
    expect(formatDuration(start, "2026-10-24T08:00:40Z")).toBe("dưới 1 phút");
  });

  it("ghép giờ và phút", () => {
    expect(formatDuration(start, "2026-10-24T10:05:00Z")).toBe("2 giờ 5 phút");
    expect(formatDuration(start, "2026-10-24T08:15:00Z")).toBe("15 phút");
  });

  it("từ 1 ngày trở lên bỏ phút", () => {
    expect(formatDuration(start, "2026-10-25T11:20:00Z")).toBe("1 ngày 3 giờ");
  });
});

describe("formatSeconds", () => {
  it("đổi số giây ra chữ như formatDuration", () => {
    expect(formatSeconds(30)).toBe("dưới 1 phút");
    expect(formatSeconds(3 * 3600 + 120)).toBe("3 giờ 2 phút");
  });
});
