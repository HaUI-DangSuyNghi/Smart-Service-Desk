import { describe, expect, it } from "vitest";
import { resolveView } from "../../../../../src/apps/servicedesk/tickets/board/useBoardView";

describe("resolveView", () => {
  it("không được xem Board thì luôn là Bảng", () => {
    expect(resolveView("board", "board", false)).toBe("table");
  });

  it("URL thắng nơi lưu", () => {
    expect(resolveView("board", "table", true)).toBe("board");
    expect(resolveView("table", "board", true)).toBe("table");
  });

  it("không có URL thì theo nơi lưu; không có gì thì là Bảng", () => {
    expect(resolveView(null, "board", true)).toBe("board");
    expect(resolveView(null, null, true)).toBe("table");
  });
});
