import { expect, test } from "@playwright/test";
import { mockWorkCalendar } from "./workCalendarFixture";

const intakeId = "11111111-1111-4111-8111-111111111111";
const reviewId = "22222222-2222-4222-8222-222222222222";

for (const width of [360, 1440]) {
  test(`danh sách điều phối có tiếp nhận, duyệt và giao việc ở ${width}px`, async ({ page }) => {
    await page.setViewportSize({ width, height: 1000 });
    const fixture = await mockWorkCalendar(page, ["task:handle", "task:assign"]);
    const tasks = ["intake", "review", "handle"].map((key, index) => ({
      id: `task-${key}`,
      processInstanceId: `process-${key}`,
      key,
      name:
        key === "intake"
          ? "Tiếp nhận thông tin"
          : key === "review"
            ? "Duyệt yêu cầu"
            : "Xử lý sự cố",
      candidateGroups: ["lab-a:coordinator"],
      assignee: null,
      createdAt: "2026-10-31T01:00:00Z",
      processKey: "service-desk-intake-reviewed",
      businessKey: index === 0 ? intakeId : reviewId,
    }));
    await page.route("**/api/hpdi/v1/workspaces/*/inbox?*", (route) =>
      route.fulfill({ json: { items: tasks, total: tasks.length, page: 0, size: 20 } }),
    );
    await page.route(`**/tickets/${intakeId}/intake`, (route) =>
      route.fulfill({
        json: {
          contractVersion: "intake-admission@1.0.0",
          ticketId: intakeId,
          contextRevision: 0,
          version: 1,
          reviewRound: 1,
          status: "MANUAL_REQUIRED",
          deadlineAt: "2026-11-01T01:00:00Z",
          lateNotice: false,
          reviewTaskMayExist: false,
          operationalAuthority: false,
          readiness: null,
          finalAssessment: null,
        },
      }),
    );
    await page.route(`**/tickets/${reviewId}`, (route) =>
      route.fulfill({
        json: {
          id: reviewId,
          workspace: "lab-a",
          creator: "another-user",
          clientRequestId: reviewId,
          title: "Máy tính không lên nguồn",
          description: "Máy tính phòng A201 bị lỗi",
          createdAt: "2026-10-31T01:00:00Z",
          state: "AWAITING_REVIEW",
          taskId: "task-review",
          assignee: null,
          process: {
            processInstanceId: "process-review",
            processKey: "service-desk-intake-reviewed",
            businessKey: reviewId,
            workspace: "lab-a",
          },
        },
      }),
    );
    await page.goto("/inbox?view=list");
    await expect(page.getByRole("button", { name: "Danh sách", exact: true })).toHaveAttribute(
      "aria-pressed",
      "true",
    );
    await expect(page.getByRole("button", { name: /Có thể nhận/ })).toHaveCount(0);
    await expect(page.getByRole("button", { name: "Nhận việc", exact: true })).toHaveCount(0);
    await expect(page.getByRole("button", { name: "Tiếp nhận", exact: true })).toBeVisible();
    await expect(
      page.getByRole("button", { name: "Duyệt và giao việc", exact: true }),
    ).toBeVisible();
    await expect(page.getByRole("button", { name: "Giao việc", exact: true })).toBeVisible();
    expect(fixture.calls.some((url) => url.includes("/queue?"))).toBe(false);
    await page.getByRole("button", { name: "Tiếp nhận", exact: true }).click();
    await expect(page.getByRole("dialog", { name: "Tiếp nhận ticket" })).toBeVisible();
    await expect(page).toHaveURL(/\/inbox\?view=list$/);
    await page.keyboard.press("Escape");
    await page.getByRole("button", { name: "Duyệt và giao việc", exact: true }).click();
    await expect(page.getByRole("dialog")).toContainText("Máy tính không lên nguồn");
    await page.keyboard.press("Escape");
    await page.getByRole("button", { name: "Lịch", exact: true }).click();
    await expect(
      page.getByRole("region", { name: "Công việc trong ngày", exact: true }),
    ).toBeVisible();
    await expect(page).toHaveURL(/view=calendar/);
    await page.reload();
    await expect(
      page.getByRole("region", { name: "Công việc trong ngày", exact: true }),
    ).toBeVisible();
  });
}
