import { createElement } from "react";
import { renderToStaticMarkup } from "react-dom/server";
import { describe, expect, it, vi } from "vitest";
import type { Assistance } from "../../../src/platform/intelligence/assistanceSchema";

const state = vi.hoisted(() => ({ data: undefined as Assistance | undefined }));
vi.mock("../../../src/platform/intelligence/useAssistance", () => ({
  useAssistance: () => ({ data: state.data, isPending: false, error: null }),
}));
import { AssistantPanel } from "../../../src/platform/intelligence/AssistantPanel";

const answer: Assistance = {
  contractVersion: "0.2.0",
  requestId: "11111111-1111-4111-8111-111111111111",
  status: "SUCCESS",
  answer: "<script>untrusted</script> Kiểm tra cáp nguồn.",
  citations: [],
  needsReview: true,
  reasonCode: "MODEL_ADVICE",
};

describe("API assistance panel", () => {
  it("shows plain text advice without claiming internal citations", () => {
    state.data = answer;
    const html = renderToStaticMarkup(
      createElement(AssistantPanel, { ask: vi.fn(), subjectLabel: "ticket", mode: "api" }),
    );
    expect(html).toContain("Chưa đối chiếu tài liệu nội bộ");
    expect(html).toContain("Gửi câu hỏi");
    expect(html).toContain("&lt;script&gt;");
    expect(html).not.toContain("<script>");
    expect(html).not.toContain("Nguồn trích dẫn");
  });

  it("disables input and explains when assistance is not configured", () => {
    state.data = undefined;
    const html = renderToStaticMarkup(
      createElement(AssistantPanel, {
        ask: vi.fn(),
        subjectLabel: "ticket",
        mode: "api",
        available: false,
      }),
    );
    expect(html).toContain("Trợ lý chưa được bật cho không gian này");
    expect(html).toMatch(/<textarea[^>]+disabled/);
    expect(html).toMatch(/<button[^>]+disabled/);
  });

  it("preserves existing verified citations", () => {
    state.data = {
      ...answer,
      contractVersion: "0.1.0",
      reasonCode: "GROUNDED",
      citations: [{ citationId: "C1", displayTitle: "Hướng dẫn", sourceVersion: "1" }],
    };
    const html = renderToStaticMarkup(
      createElement(AssistantPanel, { ask: vi.fn(), subjectLabel: "ticket" }),
    );
    expect(html).toContain("Nguồn trích dẫn");
    expect(html).toContain("Hướng dẫn");
  });
});
