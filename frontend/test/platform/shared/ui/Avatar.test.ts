import { describe, expect, it } from "vitest";
import { initialsOf } from "../../../../src/platform/shared/ui/Avatar";

describe("initialsOf", () => {
  it("lấy chữ đầu của họ và tên", () => {
    expect(initialsOf("Nguyễn Văn An")).toBe("NA");
    expect(initialsOf("Hoàng Văn Hải")).toBe("HH");
  });

  it("một từ thì lấy một chữ; rỗng thì dấu hỏi", () => {
    expect(initialsOf("Hải")).toBe("H");
    expect(initialsOf("   ")).toBe("?");
  });
});
