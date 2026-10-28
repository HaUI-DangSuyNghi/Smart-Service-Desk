import { describe, expect, it } from "vitest";
import type { AssessmentResult } from "../../../../src/apps/servicedesk/services/ticketService";
import {
  AGREE_WITH_AI,
  differsFromAi,
  reviewDefaults,
  suggestedPriority,
  toDecision,
} from "../../../../src/apps/servicedesk/tickets/reviewForm";

const suggestion: AssessmentResult = {
  contractVersion: "0.1.0",
  profileVersion: "service-desk@0.3.0",
  engine: "rules",
  rulesVersion: "0.3.0",
  modelVersion: null,
  category: "HARDWARE",
  impact: "I1",
  urgency: "U3",
  priority: "MEDIUM",
  reasonCode: "CLASSIFIED",
  evidence: [],
  explanation: null,
  needsReview: true,
  nextQuestions: [],
};

const values = {
  category: "HARDWARE" as const,
  priority: "MEDIUM" as const,
  estimateMinutes: 60,
  assignee: "",
  reason: AGREE_WITH_AI,
};

describe("reviewForm", () => {
  it("điền sẵn theo gợi ý AI và lý do đồng ý; ET chờ backend trả", () => {
    expect(reviewDefaults(suggestion)).toEqual({
      category: "HARDWARE",
      priority: "MEDIUM",
      reason: AGREE_WITH_AI,
    });
  });

  it("không có gợi ý thì để trống cho điều phối viên chọn", () => {
    const empty = reviewDefaults(null);
    expect(empty.category).toBeUndefined();
    expect(empty.reason).toBe("");
  });

  it("gợi ý ưu tiên theo ảnh hưởng × độ gấp", () => {
    expect(suggestedPriority(suggestion)).toBe("MEDIUM");
    expect(suggestedPriority(null)).toBeNull();
  });

  it("chỉ coi là khác AI khi AI có đưa ra loại", () => {
    expect(differsFromAi({ category: "HARDWARE", priority: "MEDIUM" }, suggestion)).toBe(false);
    expect(differsFromAi({ category: "NETWORK", priority: "MEDIUM" }, suggestion)).toBe(true);
    expect(differsFromAi({ category: "NETWORK", priority: "HIGH" }, null)).toBe(false);
  });

  it("để chờ ca sau thì không giao ai; ET bằng ET mặc định của backend thì không gửi", () => {
    const decision = toDecision(values, suggestion, null, 60);
    expect(decision).toMatchObject({ assignee: null, impact: "I1", urgency: "U3", reason: AGREE_WITH_AI });
    expect(decision).not.toHaveProperty("estimateMinutes");
    expect(toDecision({ ...values, estimateMinutes: 90 }, suggestion, null, 60).estimateMinutes).toBe(90);
    // Chưa tải được ET mặc định thì gửi số đang nhập
    expect(toDecision(values, suggestion).estimateMinutes).toBe(60);
  });

  it("đổi loại khác AI thì không gửi ảnh hưởng / độ gấp của AI", () => {
    const decision = toDecision({ ...values, category: "NETWORK", reason: "Lỗi mạng" }, suggestion);
    expect(decision).not.toHaveProperty("impact");
    expect(decision).not.toHaveProperty("urgency");
  });

  it("ticket qua tiếp nhận gửi kèm revision và version", () => {
    expect(toDecision(values, suggestion, { contextRevision: 3, version: 7 })).toMatchObject({
      expectedContextRevision: 3,
      expectedIntakeVersion: 7,
    });
    expect(toDecision(values, suggestion)).not.toHaveProperty("expectedContextRevision");
  });
});
