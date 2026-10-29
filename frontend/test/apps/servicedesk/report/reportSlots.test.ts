import { describe, expect, it } from "vitest";
import {
  detectSlots,
  nextQuestion,
  normalizeAnswer,
} from "../../../../src/apps/servicedesk/report/reportSlots";

describe("detectSlots", () => {
  it("nhận ra phòng, số máy, phạm vi và mức gấp ngay trong câu mô tả", () => {
    expect(detectSlots("Máy 15 phòng 1304 không lên nguồn, sắp thi rồi")).toEqual({
      "location.room": "1304",
      "device.identifier": "15",
      workBlocked: "true",
    });
    expect(detectSlots("Cả phòng a201 mất wifi").affectedScope).toBe("WHOLE_ROOM");
    expect(detectSlots("Vài máy không vào mạng").affectedScope).toBe("MULTIPLE");
  });
});

describe("nextQuestion", () => {
  it("chỉ hỏi phần còn thiếu, theo thứ tự phòng → phạm vi → số máy → dừng việc", () => {
    const problem = "Máy tính không lên nguồn";
    expect(nextQuestion(problem, {})?.slot).toBe("location.room");
    expect(nextQuestion(problem, { "location.room": "1304" })?.slot).toBe("affectedScope");
    expect(
      nextQuestion(problem, { "location.room": "1304", affectedScope: "INDIVIDUAL" })?.slot,
    ).toBe("device.identifier");
  });

  it("lỗi cả phòng hoặc không nói về thiết bị thì không hỏi số máy", () => {
    const answers = { "location.room": "1304", affectedScope: "WHOLE_ROOM" } as const;
    expect(nextQuestion("Mất mạng", answers)?.slot).toBe("workBlocked");
  });

  it("bỏ qua cũng tính là đã trả lời; hết câu thì trả null", () => {
    const answers = {
      "location.room": null,
      affectedScope: null,
      "device.identifier": null,
      workBlocked: null,
    };
    expect(nextQuestion("Máy không lên nguồn", answers)).toBeNull();
  });
});

describe("normalizeAnswer", () => {
  it("lấy số phòng / số máy trong câu, không nhận ra thì giữ nguyên chữ", () => {
    expect(normalizeAnswer("location.room", "phòng a201")).toBe("A201");
    expect(normalizeAnswer("device.identifier", "máy số 7")).toBe("7");
    expect(normalizeAnswer("location.room", "phòng thầy Nam")).toBe("phòng thầy Nam");
  });
});
