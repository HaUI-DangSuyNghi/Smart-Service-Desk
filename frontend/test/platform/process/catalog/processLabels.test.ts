import { describe, expect, it } from "vitest";
import { createProcessLabels } from "../../../../src/platform/process/catalog/processLabels";
import type { ProcessDescriptor } from "../../../../src/platform/services";

const CATALOG: ProcessDescriptor[] = [
  {
    processKey: "equipment-approval",
    name: "Mượn thiết bị",
    canStart: true,
    tasks: [
      {
        key: "approve",
        name: "Duyệt yêu cầu",
        outcomes: [
          { key: "approved", label: "Duyệt" },
          { key: "rejected", label: "Từ chối" },
        ],
      },
      // Backend trả name = key khi app chưa khai báo nhãn
      { key: "handover", name: "handover", outcomes: [{ key: "delivered", label: "delivered" }] },
    ],
    startFields: [{ key: "title", label: "Thiết bị cần mượn", multiline: false }],
  },
];

describe("createProcessLabels", () => {
  const labels = createProcessLabels(CATALOG);

  it("trả nhãn tiếng Việt của quy trình, bước và kết quả", () => {
    expect(labels.processName("equipment-approval")).toBe("Mượn thiết bị");
    expect(labels.taskName("equipment-approval", "approve")).toBe("Duyệt yêu cầu");
    expect(labels.outcomeLabel("equipment-approval", "rejected")).toBe("Từ chối");
  });

  it("giữ thứ tự kết quả backend khai báo", () => {
    expect(labels.outcomes("equipment-approval", "approve").map((o) => o.key)).toEqual([
      "approved",
      "rejected",
    ]);
  });

  it("bước chưa có nhãn thì dùng tên BPMN, không có thì dùng key", () => {
    expect(labels.taskName("equipment-approval", "handover", "Confirm handover")).toBe(
      "Confirm handover",
    );
    expect(labels.taskName("equipment-approval", "handover")).toBe("handover");
  });

  it("trả ô nhập lúc tạo theo quy trình", () => {
    expect(labels.startFields("equipment-approval").map((field) => field.label)).toEqual([
      "Thiết bị cần mượn",
    ]);
  });

  it("quy trình lạ hoặc danh mục chưa tải thì trả key, không lỗi", () => {
    const empty = createProcessLabels([]);
    expect(empty.processName("new-process")).toBe("new-process");
    expect(empty.taskName("new-process", "review", "Review")).toBe("Review");
    expect(empty.outcomes("new-process", "review")).toEqual([]);
    expect(empty.outcomeLabel("new-process", "approved")).toBe("approved");
    expect(empty.startFields("new-process")).toEqual([]);
  });
});
