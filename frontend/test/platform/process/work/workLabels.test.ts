import { describe, expect, it } from "vitest";
import {
  estimateBasisText,
  formatMinutes,
  isEscalated,
  suggestionReasonText,
} from "../../../../src/platform/process/work/workLabels";

describe("workLabels", () => {
  it("đổi số phút thành giờ phút", () => {
    expect(formatMinutes(90)).toBe("1 giờ 30 phút");
    expect(formatMinutes(45)).toBe("45 phút");
  });

  it("ghi rõ nguồn của thời gian ước tính", () => {
    expect(estimateBasisText({ minutes: 60, basis: "DEFAULT" })).toBe("Mặc định theo loại");
    expect(estimateBasisText({ minutes: 60, basis: "COORDINATOR" })).toBe("Điều phối viên đặt");
    expect(estimateBasisText({ minutes: 40, basis: "SIMILAR", sampleSize: 42 })).toBe(
      "Trung vị 42 việc tương tự",
    );
  });

  it("giải thích đề xuất người xử lý bằng tiếng Việt theo từng nhánh", () => {
    expect(suggestionReasonText({ group: "manual", recommended: null, reason: "OTHER requires manual" }))
      .toContain("tự chọn");
    expect(suggestionReasonText({ group: "network", recommended: null, reason: "No eligible" }))
      .toContain("không còn ai đang trực");
    expect(
      suggestionReasonText({
        group: "network",
        recommended: "agent-a",
        reason: "Minimum-load band...; ESTIMATE_EXCEEDS_SHIFT: compare",
      }),
    ).toContain("dài hơn thời gian còn lại");
  });

  it("nâng bậc khi ưu tiên hiệu lực khác ưu tiên gốc", () => {
    expect(isEscalated({ priority: "HIGH", basePriority: "MEDIUM" })).toBe(true);
    expect(isEscalated({ priority: "LOW", basePriority: "LOW" })).toBe(false);
  });
});
