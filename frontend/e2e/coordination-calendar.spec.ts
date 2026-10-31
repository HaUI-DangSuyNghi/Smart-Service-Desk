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
    const url = new URL(route.request().url());
    requests.push(url.toString());
    return route.fulfill({ json: { items: tasks, total: tasks.length, page: 0, size: 100 } });
  });
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
  test(`lịch điều phối dùng việc của workspace và có thao tác tiếp nhận ở ${width}px`, async ({
    page,
  }) => {
    await page.setViewportSize({ width, height: 1000 });
    const { fixture, requests } = await setup(page);
    await page.goto("/inbox");
    await expect(page.getByRole("heading", { name: "Lịch điều phối", exact: true })).toBeVisible();
    await expect(page.getByRole("heading", { name: "Lịch của tôi", exact: true })).toHaveCount(0);
    await expect(page.getByText("Ngày phát sinh công việc", { exact: true })).toBeVisible();
    await expect(page.getByText("Hạn hoàn thành", { exact: true })).toHaveCount(0);
    await expect(
      page.getByRole("button", { name: "Phát sinh trong ngày 2", exact: true }),
    ).toBeVisible();
    const region = page.getByRole("region", { name: "Việc cần điều phối" });
    await expect(region.getByRole("article")).toHaveCount(2);
    await expect(region.getByRole("button", { name: "Tiếp nhận", exact: true })).toBeVisible();
    await expect(
      region.getByRole("button", { name: "Duyệt và giao việc", exact: true }),
    ).toBeVisible();
    await expect(region.getByText(/Ước tính/)).toHaveCount(0);
    await expect(region.getByRole("button", { name: "Bắt đầu", exact: true })).toHaveCount(0);
    expect(fixture.calls.some((value) => value.includes("/queue?"))).toBe(false);
    expect(requests.some((value) => value.includes("scope=ALL"))).toBe(true);
    await region.getByRole("button", { name: "Tiếp nhận", exact: true }).click();
    await expect(page.getByRole("dialog", { name: "Tiếp nhận ticket" })).toBeVisible();
    await page.keyboard.press("Escape");
    await page.getByRole("button", { name: "Tồn từ trước 1", exact: true }).click();
    await expect(region.getByRole("article")).toHaveCount(1);
    await expect(region.getByRole("button", { name: "Giao lại", exact: true })).toBeVisible();
    await page.getByRole("button", { name: "Tất cả đang mở 3", exact: true }).click();
    await expect(region.getByRole("article")).toHaveCount(3);
    await page.getByRole("button", { name: "Tình trạng giao việc", exact: true }).click();
    await page.getByRole("menuitem", { name: "Chưa giao", exact: true }).click();
    await expect(region.getByRole("article")).toHaveCount(2);
    await page.getByLabel("Tìm việc điều phối", { exact: true }).fill("tiep nhan");
    await expect(region.getByRole("article")).toHaveCount(1);
    await expect(region).toContainText("Tiếp nhận thông tin");
    const overflow = await page.evaluate(
      () => document.documentElement.scrollWidth > innerWidth + 1,
    );
    expect(overflow).toBe(false);
    await page.screenshot({ path: `../.local/coordination-calendar-${width}.png` });
  });
}

test("lịch điều phối không bỏ sót bước tiếp nhận ở trang thứ hai", async ({ page }) => {
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
    const index = Number(url.searchParams.get("page"));
    const size = Number(url.searchParams.get("size"));
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
  await expect(
    page.getByRole("button", { name: "Phát sinh trong ngày 1", exact: true }),
  ).toBeVisible();
  await expect(
    page
      .getByRole("region", { name: "Việc cần điều phối" })
      .getByRole("button", { name: "Tiếp nhận", exact: true }),
  ).toBeVisible();
  await expect(page.getByRole("button", { name: "Tồn từ trước 100", exact: true })).toBeVisible();
});

test("đổi workspace không giữ lại công việc của workspace cũ", async ({ page }) => {
  await setup(page);
  await page.route("**/api/hpdi/v1/workspaces/lab-b/inbox?*", (route) =>
    route.fulfill({ json: { items: [], total: 0, page: 0, size: 100 } }),
  );
  await page.goto("/inbox");
  await expect(
    page.getByRole("region", { name: "Việc cần điều phối" }).getByRole("article"),
  ).toHaveCount(2);
  await page.getByRole("button", { name: /Phòng máy A/ }).click();
  await page.getByRole("menuitem", { name: /Phòng máy B/ }).click();
  await expect(
    page.getByRole("region", { name: "Việc cần điều phối" }).getByRole("article"),
  ).toHaveCount(0);
});
