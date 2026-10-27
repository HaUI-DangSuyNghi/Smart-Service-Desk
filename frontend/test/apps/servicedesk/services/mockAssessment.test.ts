import { describe, expect, it } from "vitest";
import {
  assessText,
  MISSING_INFO_QUESTIONS,
  unavailableAssessment,
} from "../../../../src/apps/servicedesk/services/mock/mockAssessment";

describe("mockAssessment", () => {
  it("phòng thi mất mạng sát giờ là mạng, khẩn cấp", () => {
    const result = assessText(
      "Phòng thi 1304 mất mạng, thi sau 15 phút",
      "Cả phòng 1304 không vào được mạng.",
    );
    expect(result).toMatchObject({
      status: "READY",
      category: "NETWORK",
      impact: "I3",
      urgency: "U4",
      priority: "CRITICAL",
      reasonCode: "CLASSIFIED",
    });
    expect(result.evidence?.[0]).toEqual({ field: "title", text: "mạng" });
  });

  it("không đủ thông tin thì không đoán loại, trả câu hỏi gợi ý", () => {
    const result = assessText("Máy bị lỗi", "Máy không dùng được.");
    expect(result.category).toBeNull();
    expect(result.reasonCode).toBe("INSUFFICIENT_INFORMATION");
    expect(result.nextQuestions).toEqual(MISSING_INFO_QUESTIONS);
  });

  it("AI không khả dụng thì DEGRADED và cần người kiểm tra", () => {
    expect(unavailableAssessment()).toMatchObject({
      status: "DEGRADED",
      category: null,
      needsReview: true,
    });
  });
});
