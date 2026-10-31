import { describe, expect, it } from "vitest";
import type { QueueItem } from "../../../../src/platform/services";
import {
  calendarDate,
  calendarWeek,
  filterCalendarWork,
  isWorkOverdue,
  shiftCalendarDate,
  sortCalendarWork,
  workDeadline,
  workInBucket,
} from "../../../../src/platform/process/inbox/calendar/calendarModel";

const NOW = new Date(2026, 9, 31, 10).getTime();
const item = (id: string, due: Date | null, overrides: Partial<QueueItem> = {}): QueueItem => ({
  id,
  name: "Xử lý",
  label: "Kiểm tra đường truyền",
  key: "resolve",
  processKey: "sample",
  processInstanceId: id,
  businessKey: `REQ-${id}`,
  candidateGroups: [],
  assignee: "me",
  createdAt: new Date(2026, 9, 30).toISOString(),
  workState: "NOT_STARTED",
  pendingReason: null,
  pendingNote: null,
  priority: "MEDIUM",
  basePriority: "MEDIUM",
  estimate: { minutes: 30, basis: "DEFAULT" },
  workedMinutes: 0,
  remainingMinutes: 30,
  reopenCount: 0,
  sla: due
    ? {
        startBy: due.toISOString(),
        resolveBy: due.toISOString(),
        startBreached: false,
        resolveBreached: false,
        warning: false,
        clockPaused: false,
      }
    : null,
  ...overrides,
});

describe("lịch công việc theo hạn hoàn thành", () => {
  it("xếp đúng ngày ở đầu và cuối ngày theo giờ người dùng", () => {
    expect(workInBucket(item("a", new Date(2026, 9, 31, 0, 1)), "day", "2026-10-31", NOW)).toBe(
      true,
    );
    expect(workInBucket(item("b", new Date(2026, 9, 31, 23, 59)), "day", "2026-10-31", NOW)).toBe(
      true,
    );
    expect(workInBucket(item("c", new Date(2026, 10, 1)), "day", "2026-10-31", NOW)).toBe(false);
  });

  it("việc tạo hôm qua vẫn hiện hôm nay theo hạn hoàn thành", () => {
    const task = item("a", new Date(2026, 9, 31, 15));
    expect(calendarDate(new Date(task.createdAt))).toBe("2026-10-30");
    expect(workInBucket(task, "day", "2026-10-31", NOW)).toBe(true);
  });

  it("nhóm quá hạn giữ cả việc tồn từ ngày trước và không lệ thuộc ngày đang chọn", () => {
    const task = item("a", new Date(2026, 9, 30, 15));
    expect(workInBucket(task, "overdue", "2026-11-15", NOW)).toBe(true);
    expect(workInBucket(task, "day", "2026-10-31", NOW)).toBe(false);
  });

  it("không tự báo quá hạn khi SLA đang tạm dừng, trừ khi backend đã xác nhận vi phạm", () => {
    const task = item("a", new Date(2026, 9, 30));
    task.sla = { ...task.sla!, clockPaused: true };
    expect(isWorkOverdue(task, NOW)).toBe(false);
    task.sla.resolveBreached = true;
    expect(isWorkOverdue(task, NOW)).toBe(true);
  });

  it("không gán giờ giả cho việc chưa có SLA hoặc hạn không hợp lệ", () => {
    const task = item("a", null);
    expect(workInBucket(task, "undated", "2026-10-31", NOW)).toBe(true);
    expect(workInBucket(task, "day", "2026-10-31", NOW)).toBe(false);
    const invalid = item("b", new Date());
    invalid.sla!.resolveBy = "invalid";
    expect(workDeadline(invalid)).toBeNull();
  });

  it("tuần bắt đầu thứ hai và chuyển chính xác qua tháng, năm, ngày nhuận", () => {
    expect(calendarWeek("2026-11-01")).toEqual([
      "2026-10-26",
      "2026-10-27",
      "2026-10-28",
      "2026-10-29",
      "2026-10-30",
      "2026-10-31",
      "2026-11-01",
    ]);
    expect(shiftCalendarDate("2026-12-31", 1)).toBe("2027-01-01");
    expect(shiftCalendarDate("2028-02-28", 1)).toBe("2028-02-29");
  });

  it("lọc đồng thời trạng thái và tìm không dấu theo tiêu đề hoặc mã yêu cầu", () => {
    const tasks = [item("a", null), item("b", null, { workState: "PENDING" })];
    expect(filterCalendarWork(tasks, "DUONG TRUYEN", "PENDING").map((task) => task.id)).toEqual([
      "b",
    ]);
    expect(filterCalendarWork(tasks, "req-a", "").map((task) => task.id)).toEqual(["a"]);
  });

  it("sắp hạn tăng dần, cùng giờ ưu tiên việc khẩn cấp, không thay đổi mảng đầu vào", () => {
    const due = new Date(2026, 9, 31, 15);
    const tasks = [
      item("low", due, { priority: "LOW" }),
      item("urgent", due, { priority: "CRITICAL" }),
      item("first", new Date(2026, 9, 31, 11)),
    ];
    expect(sortCalendarWork(tasks).map((task) => task.id)).toEqual(["first", "urgent", "low"]);
    expect(tasks[0].id).toBe("low");
  });
});
