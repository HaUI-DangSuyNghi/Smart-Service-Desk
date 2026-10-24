import { describe, expect, it } from "vitest";
import { lastDays, summarize } from "../../../../src/platform/data/dashboard/dashboardRange";
import type { ProcessCount } from "../../../../src/platform/services";

const count = (overrides: Partial<ProcessCount>): ProcessCount => ({
  processKey: "p",
  started: 0,
  running: 0,
  completed: 0,
  averageCompletionSeconds: null,
  waitingClaim: 0,
  inProgress: 0,
  ...overrides,
});

describe("lastDays", () => {
  it("7 ngày tính cả hôm nay, từ 0 giờ ngày đầu tới 0 giờ ngày mai (giờ máy)", () => {
    const range = lastDays(7, new Date(2026, 9, 24, 15, 30));
    expect(new Date(range.from)).toEqual(new Date(2026, 9, 18));
    expect(new Date(range.to)).toEqual(new Date(2026, 9, 25));
  });
});

describe("summarize", () => {
  it("cộng các quy trình, thời gian trung bình tính theo trọng số số yêu cầu đã xong", () => {
    const totals = summarize([
      count({ started: 3, completed: 1, averageCompletionSeconds: 600, waitingClaim: 2 }),
      count({ started: 5, running: 2, completed: 3, averageCompletionSeconds: 200, inProgress: 1 }),
    ]);
    expect(totals).toEqual({
      started: 8,
      running: 2,
      completed: 4,
      waitingClaim: 2,
      inProgress: 1,
      // (600 × 1 + 200 × 3) / 4
      averageCompletionSeconds: 300,
    });
  });

  it("chưa có yêu cầu nào xong thì không có thời gian trung bình", () => {
    expect(summarize([count({ started: 2, running: 2 })]).averageCompletionSeconds).toBeNull();
    expect(summarize([]).started).toBe(0);
  });
});
