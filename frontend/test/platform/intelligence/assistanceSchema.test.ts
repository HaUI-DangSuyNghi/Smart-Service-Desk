import { describe, expect, it } from "vitest";

import { parseAssistance } from "../../../src/platform/intelligence/assistanceSchema";

const response = {
  contractVersion: "0.1.0",
  requestId: "11111111-1111-4111-8111-111111111111",
  status: "SUCCESS",
  answer: "Exact excerpt. [C1]",
  citations: [{ citationId: "C1", displayTitle: "Guide", sourceVersion: "1" }],
  needsReview: true,
  reasonCode: "GROUNDED",
};

describe("assistance contract", () => {
  it("accepts model advice only with its own version and no fabricated citations", () => {
    const advice = {
      ...response,
      contractVersion: "0.2.0",
      reasonCode: "MODEL_ADVICE",
      citations: [],
    };
    expect(parseAssistance(advice).reasonCode).toBe("MODEL_ADVICE");
    expect(() => parseAssistance({ ...advice, contractVersion: "0.1.0" })).toThrow();
    expect(() => parseAssistance({ ...advice, citations: response.citations })).toThrow();
    expect(() => parseAssistance({ ...advice, needsReview: false })).toThrow();
    expect(() => parseAssistance({ ...advice, answer: "  " })).toThrow();
  });
  it("accepts escaped text as data and never transforms it into HTML", () => {
    const value = parseAssistance({ ...response, answer: "<script>synthetic</script> [C1]" });
    expect(value.answer).toContain("<script>");
  });

  it.each([
    { ...response, sourceId: "internal-reference" },
    { ...response, answer: null },
    { ...response, citations: [] },
    { ...response, needsReview: false },
    { ...response, status: "DEGRADED" },
    { ...response, status: "INSUFFICIENT_EVIDENCE" },
    { ...response, citations: [...response.citations, ...response.citations] },
    { ...response, citations: [{ ...response.citations[0], citationId: "C9" }] },
    { ...response, citations: [{ ...response.citations[0], sourceRef: "javascript:synthetic" }] },
  ])("rejects inconsistent or private response fields", (value) => {
    expect(() => parseAssistance(value)).toThrow("ASSISTANCE_CONTRACT_FAILED");
  });

  it("accepts explicit refusal only with no answer and no citations", () => {
    expect(
      parseAssistance({
        ...response,
        status: "INSUFFICIENT_EVIDENCE",
        reasonCode: "INSUFFICIENT_EVIDENCE",
        answer: null,
        citations: [],
      }).status,
    ).toBe("INSUFFICIENT_EVIDENCE");
  });
});
