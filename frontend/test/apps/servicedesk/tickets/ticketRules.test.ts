import { describe, expect, it } from "vitest";
import type {
  Assessment,
  AssessmentResult,
} from "../../../../src/apps/servicedesk/services/ticketService";
import {
  aiSuggestion,
  awaitsReview,
  isHandling,
  priorityOf,
} from "../../../../src/apps/servicedesk/tickets/ticketRules";

describe("ticketRules", () => {
  it("ưu tiên theo bảng ảnh hưởng × độ gấp (mục 4)", () => {
    expect(priorityOf("I1", "U1")).toBe("LOW");
    expect(priorityOf("I1", "U4")).toBe("HIGH");
    expect(priorityOf("I3", "U4")).toBe("CRITICAL");
    expect(priorityOf("I4", "U1")).toBe("HIGH");
  });
});

const result = (overrides: Partial<AssessmentResult>): AssessmentResult => ({
  contractVersion: "0.1.0",
  profileVersion: "service-desk@0.3.0",
  engine: "rules",
  rulesVersion: "0.3.0",
  modelVersion: null,
  category: "NETWORK",
  priority: "HIGH",
  impact: "I2",
  urgency: "U3",
  reasonCode: "CLASSIFIED",
  evidence: [],
  explanation: null,
  needsReview: true,
  nextQuestions: [],
  ...overrides,
});

const assessment = (status: Assessment["status"], value: AssessmentResult | null): Assessment => ({
  status,
  version: 1,
  errorCode: null,
  result: value,
});

describe("aiSuggestion", () => {
  it("chỉ dùng kết quả READY đã phân loại", () => {
    expect(aiSuggestion(assessment("READY", result({})))?.category).toBe("NETWORK");
    expect(aiSuggestion(assessment("PENDING", null))).toBeNull();
    expect(aiSuggestion(assessment("DEGRADED", null))).toBeNull();
    expect(aiSuggestion(null)).toBeNull();
  });

  it("thiếu thông tin thì OTHER/MEDIUM chỉ là mặc định, không phải gợi ý", () => {
    const fallback = result({ category: "OTHER", priority: "MEDIUM", reasonCode: "INSUFFICIENT_INFORMATION" });
    expect(aiSuggestion(assessment("READY", fallback))).toBeNull();
  });
});

describe("awaitsReview / isHandling", () => {
  it("mở lại chưa giao lại là bước duyệt, đã giao lại là bước xử lý", () => {
    expect(awaitsReview({ state: "AWAITING_REVIEW" })).toBe(true);
    expect(awaitsReview({ state: "REOPENED", workState: null })).toBe(true);
    expect(isHandling({ state: "REOPENED", workState: null })).toBe(false);
    expect(isHandling({ state: "REOPENED", workState: "NOT_STARTED" })).toBe(true);
    expect(isHandling({ state: "INTAKE" })).toBe(false);
  });
});
