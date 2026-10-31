import { expect, test, type Page } from "@playwright/test";
import { mockWorkCalendar } from "./workCalendarFixture";

test.use({ timezoneId: "Asia/Bangkok", locale: "vi-VN" });
const id = "11111111-1111-4111-8111-111111111111";
const makeTask = (key: string, createdAt: string, assignee: string | null = null) => ({
  id: `coord-${key}`,
  processInstanceId: `process-${key}`,
  key,
  name:
    key === "intake" ? "Tiếp nhận thông tin" : key === "review" ? "Duyệt yêu cầu" : "Xử lý sự cố",
  candidateGroups: ["lab-a:coordinator"],
  assignee,
  createdAt,
  processKey: "service-desk-intake-reviewed",
  businessKey: id,
});
const table = (page: Page) => page.getByRole("region", { name: "Bảng công việc", exact: true });
const setup = async (page: Page) => {
  const fixture = await mockWorkCalendar(page, ["task:handle", "task:assign"]);
  await page.route("**/api/hpdi/v1/workspaces/*/duty*", (route) => route.fulfill({ json: [] }));
  const tasks = [
    makeTask("intake", "2026-10-31T01:00:00Z"),
    makeTask("review", "2026-10-31T02:00:00Z"),
    makeTask("handle", "2026-10-30T01:00:00Z", "agent-user"),
    { ...makeTask("confirm", "2026-10-31T03:00:00Z", "requester-user"), candidateGroups: [] },
  ];
  const requests: string[] = [];
  await page.route("**/api/hpdi/v1/workspaces/*/inbox?*", (route) => {
    requests.push(route.request().url());
    return route.fulfill({ json: { items: tasks, total: tasks.length, page: 0, size: 100 } });
  });
  await page.route(`**/tickets/${id}`, (route) =>
    route.fulfill({
      json: {
        id,
        workspace: "lab-a",
        creator: "another-user",
        clientRequestId: id,
        title: "Máy tính phòng A201 không lên nguồn",
        description: "Cần hỗ trợ thiết bị",
        createdAt: "2026-10-31T01:00:00Z",
        state: "AWAITING_REVIEW",
        taskId: "coord-review",
        assignee: null,
        process: {
          processInstanceId: "process-review",
          processKey: "service-desk-intake-reviewed",
          businessKey: id,
          workspace: "lab-a",
        },
      },
    }),
  );
  await page.route(`**/tickets/${id}/intake`, (route) =>
    route.fulfill({
      json: {
        contractVersion: "intake-admission@1.0.0",
        ticketId: id,
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
  return { fixture, requests };
};

for (const width of [360, 1440]) {
  test(`điều phối có một bảng yêu cầu theo ngày và thao tác đúng vai trò ở ${width}px`, async ({
    page,
  }) => {
    await page.setViewportSize({ width, height: 1000 });
    const { fixture, requests } = await setup(page);
    await page.goto("/inbox");
    await expect(page.getByRole("table")).toHaveCount(1);
    await expect(table(page).getByRole("row")).toHaveCount(3);
    await expect(table(page)).toContainText("Máy tính phòng A201 không lên nguồn");
    await expect(page.getByRole("article")).toHaveCount(0);
    await expect(page.getByRole("group", { name: "Nhóm việc điều phối" })).toHaveCount(0);
    await expect(table(page).getByRole("button", { name: "Tiếp nhận", exact: true })).toHaveCount(
      1,
    );
    await expect(
      table(page).getByRole("button", { name: "Duyệt và giao việc", exact: true }),
    ).toHaveCount(1);
    await expect(table(page).getByRole("button", { name: "Bắt đầu", exact: true })).toHaveCount(0);
    expect(fixture.calls.some((url) => url.includes("/queue?"))).toBe(false);
    expect(requests.some((url) => url.includes("scope=ALL"))).toBe(true);
    await table(page).getByRole("button", { name: "Tiếp nhận", exact: true }).click();
    await expect(page.getByRole("dialog", { name: "Tiếp nhận ticket" })).toBeVisible();
    await page.keyboard.press("Escape");
    await page.getByRole("button", { name: "Ngày trước", exact: true }).click();
    await expect(table(page).getByRole("row")).toHaveCount(2);
    await expect(table(page).getByRole("button", { name: "Giao lại", exact: true })).toHaveCount(1);
    await page.getByRole("button", { name: "Hôm nay", exact: true }).click();
    await page.getByLabel("Tìm công việc", { exact: true }).fill("may tinh phong A201");
    await expect(table(page).getByRole("row")).toHaveCount(3);
    await page.getByRole("button", { name: "Bước công việc", exact: true }).click();
    await page.getByRole("menuitem", { name: "Tiếp nhận thông tin", exact: true }).click();
    await expect(table(page).getByRole("row")).toHaveCount(2);
    expect(await page.evaluate(() => document.documentElement.scrollWidth > innerWidth + 1)).toBe(
      false,
    );
    await page.screenshot({ path: `../.local/daily-work-coordinator-${width}.png` });
  });
}

test("bảng điều phối lấy đủ dữ liệu trang sau rồi lọc đúng ngày", async ({ page }) => {
  await setup(page);
  const tasks = [
    ...Array.from({ length: 100 }, (_, index) => ({
      ...makeTask("handle", "2026-10-30T01:00:00Z"),
      id: `older-${index}`,
    })),
    makeTask("intake", "2026-10-31T01:00:00Z"),
  ];
  await page.route("**/api/hpdi/v1/workspaces/*/inbox?*", (route) => {
    const url = new URL(route.request().url());
    const index = Number(url.searchParams.get("page")),
      size = Number(url.searchParams.get("size"));
    return route.fulfill({
      json: {
        items: tasks.slice(index * size, (index + 1) * size),
        total: tasks.length,
        page: index,
        size,
      },
    });
  });
  await page.goto("/inbox?view=calendar");
  await expect(table(page).getByRole("row")).toHaveCount(2);
  await expect(table(page).getByRole("button", { name: "Tiếp nhận", exact: true })).toBeVisible();
});

test("đổi workspace không giữ lại bảng công việc cũ", async ({ page }) => {
  await setup(page);
  await page.route("**/api/hpdi/v1/workspaces/lab-b/inbox?*", (route) =>
    route.fulfill({ json: { items: [], total: 0, page: 0, size: 100 } }),
  );
  await page.goto("/inbox");
  await expect(table(page).getByRole("row")).toHaveCount(3);
  await page.getByRole("button", { name: /Phòng máy A/ }).click();
  await page.getByRole("menuitem", { name: /Phòng máy B/ }).click();
  await expect(page.getByRole("table")).toHaveCount(0);
  await expect(
    page.getByText("Không có công việc trong ngày đã chọn.", { exact: true }),
  ).toBeVisible();
});
