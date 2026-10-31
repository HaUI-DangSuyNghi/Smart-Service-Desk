import { describe, expect, it } from "vitest";
import {
  estimateBasisText,
  formatMinutes,
  isEscalated,
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

  it("nâng bậc khi ưu tiên hiệu lực khác ưu tiên gốc", () => {
    expect(isEscalated({ priority: "HIGH", basePriority: "MEDIUM" })).toBe(true);
    expect(isEscalated({ priority: "LOW", basePriority: "LOW" })).toBe(false);
  });
});
