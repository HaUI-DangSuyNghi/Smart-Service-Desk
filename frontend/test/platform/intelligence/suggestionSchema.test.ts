import { describe, expect, it } from "vitest";
import { parseSuggestion } from "../../../src/platform/intelligence/suggestionSchema";

const success = {
  contractVersion: "0.1.0",
  requestId: "req-1",
  profile: "service-desk",
  workspaceId: "lab-a",
  status: "SUCCESS",
  result: { category: "network", priority: "high", suggestedGroup: "network-team" },
  evidence: [{ type: "INPUT", field: "title", text: "mất mạng" }],
  explanation: {
    matchedRules: ["network-outage"],
    impact: null,
    urgency: null,
    reasonCode: "CLASSIFIED",
    text: "Tiêu đề nhắc tới mất mạng.",
  },
  runtime: { engine: "rules", profileVersion: "service-desk@0.3.0" },
  needsReview: true,
  error: null,
};

const degraded = {
  ...success,
  status: "DEGRADED",
  result: null,
  evidence: [],
  explanation: null,
  runtime: null,
  error: { code: "INTELLIGENCE_UNAVAILABLE" },
};

describe("parseSuggestion", () => {
  it("nhận gợi ý SUCCESS, giữ trường kết quả theo profile của app", () => {
    const value = parseSuggestion(success);
    expect(value.result).toEqual(success.result);
    expect(value.evidence[0].text).toBe("mất mạng");
  });

  it("nhận DEGRADED: không kết quả, không bằng chứng", () => {
    expect(parseSuggestion(degraded).status).toBe("DEGRADED");
  });

  it.each([
    { ...success, result: null },
    { ...success, explanation: null },
    { ...degraded, result: success.result },
    { ...degraded, evidence: success.evidence },
    { ...degraded, needsReview: false },
    { ...success, status: "UNKNOWN" },
    { ...success, result: { category: 1 } },
    null,
  ])("envelope sai thì báo lỗi hợp đồng 502", (value) => {
    expect(() => parseSuggestion(value)).toThrow("SUGGESTION_CONTRACT_FAILED");
  });
});
