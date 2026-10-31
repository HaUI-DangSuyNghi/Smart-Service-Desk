import { expect, test } from "@playwright/test";
import { calendarTask, mockWorkCalendar } from "./workCalendarFixture";

test.use({ timezoneId: "Asia/Bangkok", locale: "vi-VN" });
const table = (page: import("@playwright/test").Page) =>
  page.getByRole("region", { name: "Bảng công việc", exact: true });

test("lịch là một bảng việc được giao trong ngày phát sinh", async ({ page }) => {
  const fixture = await mockWorkCalendar(page);
  const task = {
    ...calendarTask(
      "different-due-date",
      "Việc phát sinh hôm nay, hạn ngày mai",
      "2026-11-01T11:00:00+07:00",
    ),
    createdAt: "2026-10-31T08:00:00+07:00",
  };
  fixture.setItems([task]);
  await page.goto("/inbox");
  await expect(page.getByRole("table")).toHaveCount(1);
  await expect(table(page)).toContainText(task.label);
  await expect(table(page).locator('time[datetime="2026-11-01T11:00:00+07:00"]')).toHaveCount(1);
  await expect(page.getByRole("article")).toHaveCount(0);
  await expect(page.getByRole("group", { name: "Nhóm công việc theo hạn" })).toHaveCount(0);
  await expect(page.getByText("Công việc được giao cho tôi", { exact: false })).toBeVisible();
});

test("đổi ngày chỉ hiển thị công việc phát sinh trong ngày đã chọn", async ({ page }) => {
  await mockWorkCalendar(page);
  await page.goto("/inbox");
  await expect(table(page).getByRole("row")).toHaveCount(3);
  await expect(table(page)).not.toContainText("Chuẩn bị phòng cho ngày mai");
  await page.getByRole("button", { name: "Ngày sau", exact: true }).click();
  await expect(table(page)).toContainText("Chuẩn bị phòng cho ngày mai");
  await expect(table(page)).not.toContainText("Kiểm tra máy chiếu");
  await page.getByRole("button", { name: "Hôm nay", exact: true }).click();
  await page.getByRole("button", { name: "Ngày trước", exact: true }).click();
  await expect(table(page)).toContainText("Bàn giao thiết bị còn tồn");
  await expect(table(page)).toContainText("Kiểm kê thiết bị chưa có hạn");
  await expect(table(page)).toContainText("Chưa có hạn");
  await page.getByLabel("Ngày xem lịch", { exact: true }).click();
  await page.getByRole("button", { name: "Chọn ngày 1/11/2026", exact: true }).click();
  await expect(table(page)).toContainText("Chuẩn bị phòng cho ngày mai");
});

test("tìm không dấu và lọc trạng thái trên bảng", async ({ page }) => {
  await mockWorkCalendar(page);
  await page.goto("/inbox");
  await page.getByRole("button", { name: "Trạng thái công việc", exact: true }).click();
  await page.getByRole("menuitem", { name: "Đang làm", exact: true }).click();
  await page.getByLabel("Tìm công việc", { exact: true }).fill("duong truyen");
  await expect(table(page).getByRole("row")).toHaveCount(2);
  await expect(table(page)).toContainText("Khắc phục đường truyền");
  await page.getByLabel("Tìm công việc", { exact: true }).fill("không tồn tại");
  await expect(
    page.getByText("Không có công việc trong ngày đã chọn.", { exact: true }),
  ).toBeVisible();
});

test("bắt đầu và báo xong ngay trên dòng, không mở nhầm chi tiết", async ({ page }) => {
  await mockWorkCalendar(page);
  await page.goto("/inbox");
  const row = page.getByRole("row").filter({ hasText: "Kiểm tra máy chiếu phòng A1-302" });
  await row.getByRole("button", { name: "Bắt đầu", exact: true }).click();
  await expect(row).toContainText("Đang làm");
  await expect(page).toHaveURL(/\/inbox$/);
  await row.getByRole("button", { name: "Báo xong", exact: true }).click();
  await expect(page.getByRole("dialog")).toContainText("Duyệt đặt phòng");
});

test("bảng ngày lấy đủ dữ liệu qua nhiều trang", async ({ page }) => {
  const fixture = await mockWorkCalendar(page);
  fixture.setItems([
    ...Array.from({ length: 50 }, (_, index) =>
      calendarTask(`future-${index}`, `Việc ngày khác ${index}`, "2026-11-10T11:00:00+07:00"),
    ),
    calendarTask("page-two", "Việc hôm nay ở trang thứ hai", "2026-10-31T16:00:00+07:00"),
  ]);
  await page.goto("/inbox");
  await expect(table(page)).toContainText("Việc hôm nay ở trang thứ hai");
  await expect(table(page).getByRole("row")).toHaveCount(2);
  expect(fixture.calls.some((url) => url.includes("/queue?") && url.includes("page=1"))).toBe(true);
});

test("không có quyền xử lý thì không tải bảng lịch", async ({ page }) => {
  const fixture = await mockWorkCalendar(page, ["request:create"]);
  await page.goto("/inbox");
  await expect(
    page.getByRole("heading", { name: "Không có quyền truy cập", exact: true }),
  ).toBeVisible();
  expect(fixture.calls.some((url) => url.includes("/queue?"))).toBe(false);
});

test("lỗi tải bảng có mã yêu cầu và nút thử lại", async ({ page }) => {
  await mockWorkCalendar(page);
  let failed = true;
  await page.route("**/api/hpdi/v1/workspaces/*/queue?*", async (route) => {
    if (failed)
      return route.fulfill({
        status: 403,
        json: { title: "Forbidden", requestId: "daily-load-error" },
      });
    await route.fallback();
  });
  await page.goto("/inbox");
  await expect(page.getByRole("alert")).toContainText("daily-load-error");
  failed = false;
  await page.getByRole("button", { name: "Thử lại", exact: true }).click();
  await expect(table(page).getByRole("row")).toHaveCount(3);
});

test("Agent vẫn có thể chuyển sang danh sách nhận việc theo quyền", async ({ page }) => {
  const fixture = await mockWorkCalendar(page);
  await page.goto("/inbox?view=list&scope=CANDIDATE");
  await expect(page.getByRole("button", { name: "Nhận việc", exact: true })).toBeVisible();
  expect(fixture.calls.some((url) => url.includes("scope=ALL"))).toBe(false);
  expect(fixture.calls.some((url) => url.includes("/queue?"))).toBe(false);
});

for (const width of [360, 768, 1440]) {
  test(`bảng ngày cuộn ngang và thao tác được ở ${width}px`, async ({ page }) => {
    await page.setViewportSize({ width, height: 1000 });
    await mockWorkCalendar(page);
    await page.goto("/inbox");
    await expect(table(page).getByRole("row")).toHaveCount(3);
    expect(await page.evaluate(() => document.documentElement.scrollWidth > innerWidth + 1)).toBe(
      false,
    );
    await page.screenshot({ path: `../.local/daily-work-agent-${width}.png` });
    const action = page
      .getByRole("row")
      .filter({ hasText: "Kiểm tra máy chiếu phòng A1-302" })
      .getByRole("button", { name: "Bắt đầu", exact: true });
    await action.scrollIntoViewIfNeeded();
    if (width === 360) expect((await action.boundingBox())!.height).toBeGreaterThanOrEqual(36);
    await action.click();
    await expect(
      page.getByRole("row").filter({ hasText: "Kiểm tra máy chiếu phòng A1-302" }),
    ).toContainText("Đang làm");
  });
}
