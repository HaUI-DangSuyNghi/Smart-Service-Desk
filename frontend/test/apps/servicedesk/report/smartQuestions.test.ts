import { describe, expect, it } from "vitest";
import type { EnrichmentState } from "../../../../src/apps/servicedesk/services/enrichmentService";
import { QUESTIONS } from "../../../../src/apps/servicedesk/report/reportSlots";
import {
  fromAi,
  fromRule,
  normalizeSmartAnswer,
  pendingAiQuestion,
  smartAnswerLabel,
} from "../../../../src/apps/servicedesk/report/smartQuestions";

const question = (slot: string) => ({
  questionId: "a".repeat(24),
  slot,
  text: `Câu hỏi về ${slot}`,
  choices: [] as string[],
  correctionRequired: false,
});

const state = (overrides: Partial<EnrichmentState>) =>
  ({
    status: "AWAITING_ANSWER",
    manualReviewRequired: false,
    questions: [question("location.room"), question("affectedScope")],
    contextRevision: 1,
    ...overrides,
  }) as EnrichmentState;

describe("fromAi", () => {
  it("câu AI không kèm lựa chọn thì lấy lựa chọn theo mục; mục gõ tự do có gợi ý nhập", () => {
    const scope = fromAi(question("affectedScope"));
    expect(scope.choices?.map((choice) => choice.value)).toEqual(["INDIVIDUAL", "MULTIPLE", "WHOLE_ROOM"]);
    const room = fromAi(question("location.room"));
    expect(room.choices).toBeUndefined();
    expect(room.placeholder).toBe("Phòng xảy ra sự cố");
  });

  it("giữ cờ đính chính khi AI thấy thông tin mâu thuẫn", () => {
    expect(fromAi({ ...question("location.room"), correctionRequired: true }).correction).toBe(true);
  });
});

describe("pendingAiQuestion", () => {
  it("chỉ hỏi khi AI đang chờ trả lời, bỏ câu người dùng đã bỏ qua", () => {
    expect(pendingAiQuestion(state({}), [])?.slot).toBe("location.room");
    expect(pendingAiQuestion(state({}), ["location.room"])?.slot).toBe("affectedScope");
    expect(pendingAiQuestion(state({}), ["location.room", "affectedScope"])).toBeNull();
  });

  it("đã đủ, cần người xem, hoặc backend tắt thì không hỏi nữa", () => {
    expect(pendingAiQuestion(state({ status: "READY" }), [])).toBeNull();
    expect(pendingAiQuestion(state({ manualReviewRequired: true }), [])).toBeNull();
    expect(pendingAiQuestion(null, [])).toBeNull();
  });
});

describe("câu trả lời", () => {
  it("chuẩn hóa phòng / số máy như luật FE, mục khác giữ nguyên", () => {
    expect(normalizeSmartAnswer("location.room", "phòng a201")).toBe("A201");
    expect(normalizeSmartAnswer("timeWindow", " sáng mai ")).toBe("sáng mai");
  });

  it("hiển thị nhãn lựa chọn thay cho mã, bỏ qua thì ghi rõ", () => {
    const scope = fromRule(QUESTIONS.affectedScope);
    expect(smartAnswerLabel(scope, "WHOLE_ROOM")).toBe("Cả phòng");
    expect(smartAnswerLabel(scope, null)).toBe("Bỏ qua");
  });
});
