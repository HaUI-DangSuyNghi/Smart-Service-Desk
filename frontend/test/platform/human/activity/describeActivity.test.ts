import { describe, expect, it } from "vitest";
import { describeActivity } from "../../../../src/platform/human/activity/describeActivity";
import { createProcessLabels } from "../../../../src/platform/process/catalog/processLabels";
import type { ActivityEntry } from "../../../../src/platform/services";

const labels = createProcessLabels([
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
    ],
    startFields: [],
  },
]);

const entry = (overrides: Partial<ActivityEntry>): ActivityEntry => ({
  id: "1",
  occurredAt: "2026-10-24T08:00:00Z",
  actor: "me",
  action: "process.start",
  resourceType: "process",
  resource: "p-1",
  processInstanceId: "p-1",
  processKey: "equipment-approval",
  details: {},
  ...overrides,
});

describe("describeActivity", () => {
  it("tạo yêu cầu kèm tên quy trình, mở trang tiến độ", () => {
    expect(describeActivity(entry({}), labels)).toMatchObject({
      title: "Tạo yêu cầu",
      detail: "Mượn thiết bị",
      href: "/processes/p-1",
    });
  });

  it("hoàn thành bước dùng nhãn tiếng Việt của bước và kết quả", () => {
    const view = describeActivity(
      entry({
        action: "task.complete",
        resourceType: "task",
        resource: "t-1",
        details: { taskKey: "approve", outcome: "rejected" },
      }),
      labels,
    );
    expect(view.title).toBe("Hoàn thành bước Duyệt yêu cầu");
    // Kết quả chỉ nằm ở outcome (tag màu), không lặp trong dòng chữ phụ
    expect(view.detail).toBe("Mượn thiết bị");
    expect(view.outcome).toBe("rejected");
    expect(labels.outcomeLabel("equipment-approval", "rejected")).toBe("Từ chối");
  });

  it("tạo tài liệu mở trang tài liệu, tải file thì không có trang để mở", () => {
    const document = entry({
      action: "metadata.create",
      resourceType: "metadata",
      resource: "m-1",
      processInstanceId: null,
      processKey: null,
    });
    expect(describeActivity(document, labels).href).toBe("/knowledge/m-1");
    const file = entry({
      action: "file.upload",
      resourceType: "file",
      processInstanceId: null,
      processKey: null,
    });
    expect(describeActivity(file, labels).href).toBeNull();
  });

  it("giao việc ghi tên bước và người được giao", () => {
    const view = describeActivity(
      entry({
        action: "task.assign",
        resourceType: "task",
        details: { taskKey: "approve", assignee: "s2" },
      }),
      labels,
      new Map([["s2", "Demo Agent"]]),
    );
    expect(view.title).toBe("Giao việc Duyệt yêu cầu");
    expect(view.detail).toBe("Mượn thiết bị · cho Demo Agent");
  });

  it("nhật ký cũ không ghi quy trình vẫn ra nhãn tiếng Việt (tra trong mọi quy trình)", () => {
    const view = describeActivity(
      entry({
        action: "task.complete",
        resourceType: "task",
        processInstanceId: null,
        processKey: null,
        details: { taskKey: "approve", outcome: "approved" },
      }),
      labels,
    );
    expect(view.title).toBe("Hoàn thành bước Duyệt yêu cầu");
    expect(view.detail).toBeNull();
    expect(labels.outcomeLabel(null, "approved")).toBe("Duyệt");
  });

  it("thao tác riêng của app chưa có nhãn thì hiện mã thao tác", () => {
    const view = describeActivity(
      entry({ action: "servicedesk.ticket.create", processKey: null }),
      labels,
    );
    expect(view.title).toBe("servicedesk.ticket.create");
    expect(view.href).toBe("/processes/p-1");
  });
});
