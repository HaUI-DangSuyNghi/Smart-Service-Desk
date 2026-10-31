import { expect, test } from "@playwright/test";
import { fileURLToPath } from "node:url";
import { calendarTask, mockWorkCalendar } from "./workCalendarFixture";

test.use({ timezoneId: "Asia/Bangkok", locale: "vi-VN" });

test("mặc định mở lịch hôm nay và sắp công việc theo hạn", async ({ page }) => {
  await mockWorkCalendar(page);
  await page.goto("/inbox");
  await expect(page.getByLabel("Ngày xem lịch", { exact: true })).toContainText("31/10/2026");
  const region = page.getByRole("region", { name: "Trong ngày", exact: true });
  await expect(region.getByRole("article")).toHaveCount(2);
  await expect(region.getByRole("article").first()).toContainText("Kiểm tra máy chiếu");
  await expect(region).toContainText("11:30");
  await expect(region).not.toContainText("Chuẩn bị phòng cho ngày mai");
  await expect(page.getByRole("button", { name: "Trong ngày 2", exact: true })).toHaveAttribute(
    "aria-pressed",
    "true",
  );
});

test("chuyển ngày, tuần và quay lại hôm nay", async ({ page }) => {
  await mockWorkCalendar(page);
  await page.goto("/inbox");
  await page.getByLabel("Ngày xem lịch", { exact: true }).click();
  await page.getByRole("button", { name: "Chọn ngày 1/11/2026", exact: true }).click();
  await expect(page.getByRole("region", { name: "Trong ngày", exact: true })).toContainText(
    "Chuẩn bị phòng cho ngày mai",
  );
  await page.getByRole("button", { name: "Tuần sau", exact: true }).click();
  await expect(page.getByLabel("Ngày xem lịch", { exact: true })).toContainText("08/11/2026");
  await page.getByRole("button", { name: "Hôm nay", exact: true }).click();
  await expect(page.getByLabel("Ngày xem lịch", { exact: true })).toContainText("31/10/2026");
  await page.getByRole("button", { name: /Xem ngày 1\/11\/2026/ }).click();
  await expect(page.getByLabel("Ngày xem lịch", { exact: true })).toContainText("01/11/2026");
});

test("việc quá hạn và chưa có hạn luôn có thể truy cập riêng", async ({ page }) => {
  await mockWorkCalendar(page);
  await page.goto("/inbox");
  await page.getByRole("button", { name: "Quá hạn 1", exact: true }).click();
  await expect(page.getByRole("region", { name: "Quá hạn", exact: true })).toContainText(
    "Bàn giao thiết bị còn tồn",
  );
  await page.getByRole("button", { name: "Chưa có hạn 1", exact: true }).click();
  const region = page.getByRole("region", { name: "Chưa có hạn", exact: true });
  await expect(region).toContainText("Kiểm kê thiết bị");
  await expect(region.locator("time")).toHaveCount(0);
});

test("tìm không dấu và lọc trạng thái cùng lúc", async ({ page }) => {
  await mockWorkCalendar(page);
  await page.goto("/inbox");
  await page.getByRole("button", { name: "Trạng thái công việc", exact: true }).click();
  await page.getByRole("menuitem", { name: "Đang làm", exact: true }).click();
  await page.getByLabel("Tìm công việc").fill("duong truyen");
  const region = page.getByRole("region", { name: "Trong ngày", exact: true });
  await expect(region.getByRole("article")).toHaveCount(1);
  await expect(region).toContainText("Khắc phục đường truyền");
  await page.getByLabel("Tìm công việc").fill("không tồn tại");
  await expect(region).toContainText("Không có công việc phù hợp bộ lọc.");
});

test("bắt đầu xử lý ngay trên lịch và làm mới trạng thái", async ({ page }) => {
  await mockWorkCalendar(page);
  await page.goto("/inbox");
  const item = page.getByRole("article", { name: "Kiểm tra máy chiếu phòng A1-302", exact: true });
  await expect(item.getByRole("link")).toHaveAttribute("href", "/roomrequest/requests/projector");
  await item.getByRole("button", { name: "Bắt đầu", exact: true }).click();
  await expect(item).toContainText("Đang làm");
  await item.getByRole("button", { name: "Báo xong", exact: true }).click();
  await expect(
    page.getByRole("dialog").getByRole("heading", { name: "Duyệt đặt phòng", exact: true }),
  ).toBeVisible();
});

test("lịch không bỏ sót việc ở trang dữ liệu thứ hai", async ({ page }) => {
  const fixture = await mockWorkCalendar(page);
  fixture.setItems([
    ...Array.from({ length: 50 }, (_, index) =>
      calendarTask(`future-${index}`, `Việc tuần sau ${index}`, "2026-11-10T11:00:00+07:00"),
    ),
    calendarTask("page-two", "Việc hôm nay ở trang thứ hai", "2026-10-31T16:00:00+07:00"),
  ]);
  await page.goto("/inbox");
  await expect(page.getByRole("region", { name: "Trong ngày", exact: true })).toContainText(
    "Việc hôm nay ở trang thứ hai",
  );
  expect(fixture.calls.some((url) => url.includes("/queue?") && url.includes("page=1"))).toBe(true);
});

test("thiếu quyền xử lý thì không tải lịch công việc", async ({ page }) => {
  const fixture = await mockWorkCalendar(page, ["request:create"]);
  await page.goto("/inbox");
  await expect(
    page.getByRole("heading", { name: "Không có quyền truy cập", exact: true }),
  ).toBeVisible();
  expect(fixture.calls.some((url) => url.includes("/queue?"))).toBe(false);
});

test("lỗi tải lịch hiện mã yêu cầu và có thể thử lại", async ({ page }) => {
  await mockWorkCalendar(page);
  let hasFailed = true;
  await page.route("**/api/hpdi/v1/workspaces/*/queue?*", async (route) => {
    if (hasFailed)
      return route.fulfill({
        status: 403,
        json: { title: "Forbidden", requestId: "calendar-load-error" },
      });
    await route.fallback();
  });
  await page.goto("/inbox");
  await expect(page.getByRole("alert")).toContainText("calendar-load-error", { timeout: 15000 });
  hasFailed = false;
  await page.getByRole("button", { name: "Thử lại", exact: true }).click();
  await expect(
    page.getByRole("region", { name: "Trong ngày", exact: true }).getByRole("article"),
  ).toHaveCount(2);
});

test("giữ góc nhìn danh sách và kiểm quyền giao việc", async ({ page }) => {
  const fixture = await mockWorkCalendar(page, ["task:handle"]);
  await page.goto("/inbox?view=list&scope=CANDIDATE");
  await expect(page.getByRole("button", { name: "Nhận việc", exact: true })).toBeVisible();
  await expect(
    page
      .getByRole("group", { name: "Loại việc", exact: true })
      .getByRole("button", { name: /Tất cả việc/ }),
  ).toHaveCount(0);
  expect(fixture.calls.some((url) => url.includes("scope=ALL"))).toBe(false);
  expect(fixture.calls.some((url) => url.includes("/queue?"))).toBe(false);
});

for (const width of [360, 768, 1024, 1440]) {
  test(`lịch công việc dễ thao tác ở ${width}px`, async ({ page }) => {
    await page.setViewportSize({ width, height: 1000 });
    await mockWorkCalendar(page);
    await page.goto("/inbox");
    await expect(
      page.getByRole("region", { name: "Trong ngày", exact: true }).getByRole("article"),
    ).toHaveCount(2);
    await expect(page.getByRole("status", { name: "Đang tải", exact: true })).toBeHidden();
    const overflow = await page.evaluate(() =>
      Array.from(document.querySelectorAll("body *"))
        .filter(
          (node) =>
            node.clientWidth > 0 &&
            !["hidden", "clip"].includes(getComputedStyle(node).overflowX) &&
            node.scrollWidth > node.clientWidth + 1,
        )
        .map((node) => ({ tag: node.tagName, className: node.className })),
    );
    expect(overflow).toEqual([]);
    await page.screenshot({
      path: fileURLToPath(new URL(`../../.local/work-calendar-${width}.png`, import.meta.url)),
    });
    const action = page
      .getByRole("article", { name: "Kiểm tra máy chiếu phòng A1-302" })
      .getByRole("button", { name: "Bắt đầu", exact: true });
    if (width === 360) expect((await action.boundingBox())?.height).toBeGreaterThanOrEqual(36);
    await action.click();
    await expect(
      page.getByRole("article", { name: "Kiểm tra máy chiếu phòng A1-302" }),
    ).toContainText("Đang làm");
  });
}
