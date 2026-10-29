import { createElement } from "react";
import { renderToStaticMarkup } from "react-dom/server";
import { describe, expect, it } from "vitest";
import { AssessmentCard } from "../../../../src/apps/servicedesk/tickets/AssessmentCard";
import type { Assessment } from "../../../../src/apps/servicedesk/services/ticketService";

const model: Assessment = {
  status: "READY",
  version: 1,
  errorCode: null,
  result: {
    contractVersion: "0.1.0",
    profileVersion: "service-desk-local@1.0.0",
    engine: "model",
    rulesVersion: "servicedesk-local-facts@0.1.0",
    modelVersion: "model-pin",
    category: "HARDWARE",
    priority: null,
    impact: null,
    urgency: "U4",
    reasonCode: "MODEL_CLASSIFIED",
    evidence: [],
    explanation: null,
    needsReview: true,
    nextQuestions: ["Có bao nhiêu thiết bị bị ảnh hưởng?"],
  },
};

describe("AssessmentCard model advice", () => {
  it("shows model/rules conflicts without silently choosing a category", () => {
    const assessment: Assessment = {
      ...model,
      result: {
        ...model.result!,
        engine: "hybrid",
        category: null,
        reasonCode: "HYBRID_CONFLICT",
        cooperation: {
          provider: "Gemini API",
          modelCategory: "NETWORK",
          rulesCategory: "HARDWARE",
          modelStatus: "AVAILABLE",
          rulesStatus: "CLASSIFIED",
          decision: "CONFLICT",
        },
      },
    };
    const html = renderToStaticMarkup(createElement(AssessmentCard, { assessment }));
    expect(html).toContain("Gemini API");
    expect(html).toContain("Bộ luật");
    expect(html).toContain("Hai nguồn có kết quả khác nhau");
    expect(html).not.toContain("Loại sự cố");
  });

  it("keeps rules advice and a retry action when the model is down", () => {
    const assessment: Assessment = {
      ...model,
      result: {
        ...model.result!,
        engine: "hybrid",
        reasonCode: "RULES_SUPPORTED",
        cooperation: {
          provider: "Qwen",
          modelCategory: null,
          rulesCategory: "HARDWARE",
          modelStatus: "UNAVAILABLE",
          rulesStatus: "CLASSIFIED",
          decision: "RULES_SUPPORTED",
        },
      },
    };
    const html = renderToStaticMarkup(
      createElement(AssessmentCard, { assessment, modelEnabled: true, onRetry: () => {} }),
    );
    expect(html).toContain("Tạm không khả dụng");
    expect(html).toContain("Loại sự cố");
    expect(html).toContain("Phân tích lại bằng model");
  });
  it("keeps the hardware category and known urgency when priority is unknown", () => {
    const html = renderToStaticMarkup(createElement(AssessmentCard, { assessment: model }));
    expect(html).toContain("Model + bộ luật");
    expect(html).toContain("Loại sự cố");
    expect(html).toContain("Độ gấp");
    expect(html).not.toContain("AI chưa đủ thông tin để phân loại");
    expect(html).not.toContain("Ưu tiên");
    expect(html).toContain("Có bao nhiêu thiết bị bị ảnh hưởng?");
  });

  it("provides an explicit retry action after a provider outage", () => {
    const html = renderToStaticMarkup(
      createElement(AssessmentCard, {
        assessment: {
          status: "DEGRADED",
          version: 1,
          errorCode: "INTELLIGENCE_UNAVAILABLE",
          result: null,
        },
        onRetry: () => {},
        modelEnabled: true,
        retrying: true,
        retryError: true,
      }),
    );
    expect(html).toContain("Đang gửi yêu cầu");
    expect(html).toContain("disabled");
    expect(html).toContain('role="alert"');
  });
});
