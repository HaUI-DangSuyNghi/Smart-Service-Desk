import { describe, expect, it } from "vitest";
import { requestContent } from "../../../../src/platform/process/timeline/requestContent";
import type { ProcessStartField } from "../../../../src/platform/services";

const FIELDS: ProcessStartField[] = [
  { key: "title", label: "Tiêu đề", multiline: false },
  { key: "content", label: "Nội dung", multiline: true },
];

describe("requestContent", () => {
  it("theo thứ tự ô của form, dùng nhãn tiếng Việt", () => {
    expect(requestContent(FIELDS, { content: "Chi tiết", title: "Xin máy chiếu" })).toEqual([
      { key: "title", label: "Tiêu đề", value: "Xin máy chiếu" },
      { key: "content", label: "Nội dung", value: "Chi tiết" },
    ]);
  });

  it("bỏ ô chưa nhập; biến không có trong danh mục vẫn hiện bằng key, xếp cuối", () => {
    expect(requestContent(FIELDS, { room: "A101", title: "Họp" })).toEqual([
      { key: "title", label: "Tiêu đề", value: "Họp" },
      { key: "room", label: "room", value: "A101" },
    ]);
  });

  it("không có biến thì rỗng", () => {
    expect(requestContent(FIELDS, {})).toEqual([]);
  });
});
