import type { Page } from "@playwright/test";
import type { QueueItem, WorkState } from "../src/platform/services/workService";
import { mockHome } from "./homeFixture";

export const calendarTask = (
  id: string,
  label: string,
  deadline: string | null,
  state: WorkState = "NOT_STARTED",
): QueueItem => ({
  id,
  label,
  name: "Duyệt đặt phòng",
  key: "approve",
  processKey: "room-request",
  processInstanceId: `${id}-process`,
  businessKey: id,
  candidateGroups: ["lab-a:agent"],
  assignee: "home-user",
  createdAt: deadline ?? "2026-10-30T07:00:00Z",
  workState: state,
  pendingReason: state === "PENDING" ? "WAITING_REQUESTER" : null,
  pendingNote: null,
  priority: "HIGH",
  basePriority: "HIGH",
  estimate: { minutes: 45, basis: "DEFAULT" },
  remainingMinutes: 30,
  workedMinutes: 15,
  reopenCount: 0,
  sla: deadline
    ? {
        startBy: "2026-10-31T09:00:00+07:00",
        resolveBy: deadline,
        startBreached: false,
        resolveBreached: false,
        warning: false,
        clockPaused: false,
      }
    : null,
});

export const calendarTasks = () => [
  calendarTask("projector", "Kiểm tra máy chiếu phòng A1-302", "2026-10-31T11:30:00+07:00"),
  calendarTask(
    "network",
    "Khắc phục đường truyền phòng thực hành",
    "2026-10-31T15:00:00+07:00",
    "IN_PROGRESS",
  ),
  calendarTask("overdue", "Bàn giao thiết bị còn tồn", "2026-10-30T16:00:00+07:00"),
  calendarTask("tomorrow", "Chuẩn bị phòng cho ngày mai", "2026-11-01T11:00:00+07:00"),
  calendarTask("undated", "Kiểm kê thiết bị chưa có hạn", null, "PENDING"),
];

export const mockWorkCalendar = async (page: Page, permissions = ["task:handle"]) => {
  const calls = await mockHome(page, permissions);
  await page.clock.setFixedTime(new Date("2026-10-31T03:00:00Z"));
  let items = calendarTasks();
  await page.route("**/api/hpdi/v1/workspaces/*/queue?*", (route) => {
    calls.push(route.request().url());
    const url = new URL(route.request().url());
    const index = Number(url.searchParams.get("page") ?? 0);
    const size = Number(url.searchParams.get("size") ?? 50);
    return route.fulfill({
      json: {
        items: items.slice(index * size, (index + 1) * size),
        page: index,
        size,
        total: items.length,
        assignee: "home-user",
        loadMinutes: 150,
      },
    });
  });
  await page.route("**/api/hpdi/v1/workspaces/*/tasks/*/start", (route) => {
    const id = new URL(route.request().url()).pathname.split("/").at(-2);
    items = items.map((item) =>
      item.id === id ? { ...item, workState: "IN_PROGRESS" as const } : item,
    );
    return route.fulfill({ json: items.find((item) => item.id === id) });
  });
  return {
    calls,
    setItems: (next: QueueItem[]) => {
      items = next;
    },
  };
};
