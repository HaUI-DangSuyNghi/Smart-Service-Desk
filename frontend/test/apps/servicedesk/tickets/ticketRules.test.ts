import { describe, expect, it } from "vitest";
import {
  DEFAULT_ESTIMATES,
  priorityOf,
} from "../../../../src/apps/servicedesk/tickets/ticketRules";

describe("ticketRules", () => {
  it("ưu tiên theo bảng ảnh hưởng × độ gấp (mục 4)", () => {
    expect(priorityOf("I1", "U1")).toBe("LOW");
    expect(priorityOf("I1", "U4")).toBe("HIGH");
    expect(priorityOf("I3", "U4")).toBe("CRITICAL");
    expect(priorityOf("I4", "U1")).toBe("HIGH");
  });

  it("ước tính mặc định khớp ví dụ của plan (R2)", () => {
    expect(DEFAULT_ESTIMATES.HARDWARE.MEDIUM).toBe(60);
    expect(DEFAULT_ESTIMATES.ACCOUNT.LOW).toBe(15);
  });

  it("ưu tiên cao hơn thì ước tính không ngắn hơn", () => {
    for (const row of Object.values(DEFAULT_ESTIMATES)) {
      expect(row.LOW).toBeLessThanOrEqual(row.MEDIUM);
      expect(row.MEDIUM).toBeLessThanOrEqual(row.HIGH);
      expect(row.HIGH).toBeLessThanOrEqual(row.CRITICAL);
    }
  });
});
