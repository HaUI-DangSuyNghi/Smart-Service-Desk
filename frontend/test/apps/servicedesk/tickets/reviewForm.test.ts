import { describe, expect, it } from "vitest";
import type { Assessment } from "../../../../src/apps/servicedesk/services/ticketService";
import {
  differsFromAi,
  reviewDefaults,
  suggestedPriority,
  toDecision,
} from "../../../../src/apps/servicedesk/tickets/reviewForm";

const assessment: Assessment = {
  status: "READY",
  category: "HARDWARE",
  impact: "I1",
  urgency: "U3",
  priority: "MEDIUM",
  needsReview: false,
  nextQuestions: [],
};

describe("reviewForm", () => {
  it("điền sẵn theo gợi ý AI và ước tính mặc định", () => {
    expect(reviewDefaults(assessment)).toEqual({
      category: "HARDWARE",
      priority: "MEDIUM",
      estimateMinutes: 60,
      reason: "",
    });
  });

  it("AI không chắc thì để trống cho điều phối viên chọn", () => {
    const empty = reviewDefaults({ status: "READY", category: null, needsReview: true, nextQuestions: [] });
    expect(empty.category).toBeUndefined();
    expect(empty.estimateMinutes).toBeUndefined();
  });

  it("gợi ý ưu tiên theo ảnh hưởng × độ gấp", () => {
    expect(suggestedPriority(assessment)).toBe("MEDIUM");
    expect(suggestedPriority(null)).toBeNull();
  });

  it("chỉ coi là khác AI khi AI có đưa ra loại", () => {
    expect(differsFromAi({ category: "HARDWARE", priority: "MEDIUM" }, assessment)).toBe(false);
    expect(differsFromAi({ category: "NETWORK", priority: "MEDIUM" }, assessment)).toBe(true);
    expect(differsFromAi({ category: "NETWORK", priority: "HIGH" }, null)).toBe(false);
  });

  it("để chờ ca sau thì không giao ai", () => {
    const decision = toDecision(
      { category: "HARDWARE", priority: "MEDIUM", estimateMinutes: 60, assignee: "", reason: "" },
      assessment,
    );
    expect(decision).toMatchObject({ assignee: null, impact: "I1", urgency: "U3" });
    expect(decision.reason).toBeUndefined();
  });
});
