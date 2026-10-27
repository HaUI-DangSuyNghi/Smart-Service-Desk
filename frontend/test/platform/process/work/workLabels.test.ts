import { describe, expect, it } from "vitest";
import {
  estimateBasisText,
  formatMinutes,
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
    expect(
      estimateBasisText({
        minutes: 120,
        basis: "SIMILAR_BY_SIZE",
        sampleSize: 18,
        effortSize: "LARGE",
      }),
    ).toBe("Trung vị 18 việc tương tự (cỡ lớn)");
  });
});
