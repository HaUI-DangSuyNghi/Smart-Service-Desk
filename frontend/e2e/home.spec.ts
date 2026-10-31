import { expect, test } from "@playwright/test";
import { fileURLToPath } from "node:url";
import { homePermissions, homeRequest, mockHome } from "./homeFixture";

test("không có quyền thì Home không tải dữ liệu được bảo vệ", async ({ page }) => {
  const calls = await mockHome(page, []);
  await page.goto("/home");
  await expect(page.getByRole("navigation", { name: "Truy cập nhanh" })).toBeVisible();
  await expect(
    page
      .getByRole("navigation", { name: "Truy cập nhanh" })
      .getByRole("link", { name: "Đặt phòng" }),
  ).toBeVisible();
  await expect(
    page.getByRole("region", { name: /Công việc|Yêu cầu của tôi|Kho tri thức/ }),
  ).toHaveCount(0);
  await expect(page.getByRole("button", { name: "Tạo yêu cầu" })).toHaveCount(0);
  await expect(page.getByRole("link", { name: "Hỏi đáp tài liệu", exact: true })).toHaveCount(0);
  expect(calls.filter((url) => /\/(inbox|instances|knowledge|processes)(\?|$)/.test(url))).toEqual(
    [],
  );
});

test("quyền tạo yêu cầu chỉ tải yêu cầu cá nhân và Home không có nút tạo", async ({ page }) => {
  const calls = await mockHome(page, ["request:create"]);
  await page.goto("/home");
  const requests = page.getByRole("region", { name: "Yêu cầu của tôi", exact: true });
  await expect(requests).toContainText("lab-a-request");
  await expect(requests.getByRole("heading")).toContainText("42");
  expect(calls.some((url) => url.includes("/instances?scope=mine"))).toBe(true);
  expect(calls.filter((url) => /\/(inbox|knowledge)(\?|$)/.test(url))).toEqual([]);
  await expect(page.getByText("Yêu cầu đang mở", { exact: true })).toHaveCount(0);
  await expect(page.getByRole("button", { name: "Tạo yêu cầu", exact: true })).toHaveCount(0);
});

test("quyền xem tài liệu hiện tri thức và AI, không tải công việc hay yêu cầu", async ({
  page,
}) => {
  const calls = await mockHome(page, ["document:view"]);
  await page.goto("/home");
  await expect(page.getByRole("region", { name: "Kho tri thức" })).toContainText("Kết nối Wi-Fi");
  await expect(
    page
      .getByRole("region", { name: "Kho tri thức" })
      .getByRole("link", { name: "Hỏi đáp tài liệu" }),
  ).toHaveAttribute("href", "/knowledge-chat");
  expect(calls.filter((url) => /\/(inbox|instances)(\?|$)/.test(url))).toEqual([]);
});

test("quyền điều phối hiện lịch và thống kê, không suy ra quyền xử lý", async ({ page }) => {
  const calls = await mockHome(page, ["task:assign", "dashboard:view"]);
  await page.goto("/home");
  const shortcuts = page.getByRole("navigation", { name: "Truy cập nhanh" });
  await expect(shortcuts.getByRole("link", { name: "Lịch trực" })).toBeVisible();
  await expect(shortcuts.getByRole("link", { name: "Thống kê" })).toBeVisible();
  await expect(page.getByRole("region", { name: "Công việc", exact: true })).toHaveCount(0);
  expect(calls.filter((url) => /\/(inbox|instances|knowledge)(\?|$)/.test(url))).toEqual([]);
});

test("công việc đổi đúng nhóm và cả dòng mở đúng chi tiết", async ({ page }) => {
  await mockHome(page, homePermissions);
  await page.goto("/home");
  const work = page.getByRole("region", { name: "Công việc", exact: true });
  await expect(work).toContainText("assigned-room");
  await work.getByRole("button", { name: "Có thể nhận" }).click();
  await expect(work).toContainText("candidate-room");
  await expect(work).not.toContainText("assigned-room");
  await expect(work.getByRole("link", { name: /Duyệt đặt phòng/ })).toHaveAttribute(
    "href",
    "/roomrequest/requests/candidate-room",
  );
  await work.getByRole("button", { name: "Được giao cho tôi" }).click();
  await expect(work).toContainText("assigned-room");
});

test("chờ quyền thì hiện skeleton và không tải dữ liệu trước", async ({ page }) => {
  const calls = await mockHome(page, homePermissions);
  let release: () => void = () => {};
  const ready = new Promise<void>((resolve) => {
    release = resolve;
  });
  await page.route("**/api/hpdi/v1/workspaces/*/permissions", async (route) => {
    await ready;
    await route.fulfill({ json: [{ key: "document:view", label: "Xem tài liệu" }] });
  });
  await page.goto("/home");
  await expect(page.getByRole("status", { name: "Đang tải trang chủ" })).toBeVisible();
  expect(calls.filter((url) => /\/(inbox|instances|knowledge|processes)(\?|$)/.test(url))).toEqual(
    [],
  );
  release();
  await expect(page.getByRole("region", { name: "Kho tri thức" })).toBeVisible();
});

test("lỗi quyền hiện mã yêu cầu và không tải các khối dữ liệu", async ({ page }) => {
  const calls = await mockHome(page, homePermissions);
  await page.route("**/api/hpdi/v1/workspaces/*/permissions", (route) =>
    route.fulfill({
      status: 403,
      json: { title: "Forbidden", requestId: "home-permission-error" },
    }),
  );
  await page.goto("/home");
  await expect(page.getByRole("alert")).toContainText("home-permission-error", { timeout: 15000 });
  await expect(
    page.getByRole("region", { name: /Công việc|Yêu cầu của tôi|Kho tri thức/ }),
  ).toHaveCount(0);
  expect(calls.filter((url) => /\/(inbox|instances|knowledge|processes)(\?|$)/.test(url))).toEqual(
    [],
  );
});

test("lỗi dữ liệu có mã yêu cầu, thử lại tải được danh sách", async ({ page }) => {
  await mockHome(page, ["request:create"]);
  let hasFailed = true;
  await page.route("**/api/hpdi/v1/workspaces/*/instances?*", (route) =>
    hasFailed
      ? route.fulfill({ status: 403, json: { title: "Forbidden", requestId: "home-list-error" } })
      : route.fulfill({ json: { items: [homeRequest("lab-a")], page: 0, size: 20, total: 1 } }),
  );
  await page.goto("/home");
  const requests = page.getByRole("region", { name: "Yêu cầu của tôi", exact: true });
  await expect(requests.getByRole("alert")).toContainText("home-list-error", { timeout: 15000 });
  await expect(requests).not.toContainText("Chưa có yêu cầu nào.");
  hasFailed = false;
  await requests.getByRole("button", { name: "Thử lại" }).click();
  await expect(requests).toContainText("lab-a-request");
});

test("đổi workspace không hiển thị dữ liệu của workspace cũ", async ({ page }) => {
  await mockHome(page, ["request:create"]);
  let release: () => void = () => {};
  const ready = new Promise<void>((resolve) => {
    release = resolve;
  });
  await page.route("**/api/hpdi/v1/workspaces/lab-b/instances?*", async (route) => {
    await ready;
    await route.fulfill({ json: { items: [homeRequest("lab-b")], page: 0, size: 20, total: 1 } });
  });
  await page.goto("/home");
  const requests = page.getByRole("region", { name: "Yêu cầu của tôi", exact: true });
  await expect(requests).toContainText("lab-a-request");
  await page.getByRole("button", { name: "P Phòng máy A" }).click();
  await page.getByRole("menuitem", { name: "Phòng máy B lab-b" }).click();
  await expect(page.getByRole("status", { name: "Đang tải yêu cầu" })).toBeVisible();
  await expect(requests).not.toContainText("lab-a-request");
  release();
  await expect(requests).toContainText("lab-b-request");
});

for (const width of [360, 768, 1024, 1440]) {
  test(`Home dùng được ở ${width}px và không cuộn ngang`, async ({ page }) => {
    await page.setViewportSize({ width, height: 960 });
    await mockHome(page, homePermissions);
    await page.goto("/home");
    await expect(page.getByRole("region", { name: "Công việc", exact: true })).toContainText(
      "assigned-room",
    );
    await expect(page.getByRole("region", { name: "Kho tri thức" })).toContainText("Kết nối Wi-Fi");
    await expect(page.getByRole("status", { name: "Đang tải", exact: true })).toBeHidden();
    const overflow = await page.evaluate(() =>
      Array.from(document.querySelectorAll("body *"))
        .filter(
          (node) =>
            node.clientWidth > 0 &&
            getComputedStyle(node).overflowX !== "hidden" &&
            getComputedStyle(node).overflowX !== "clip" &&
            node.scrollWidth > node.clientWidth + 1,
        )
        .map((node) => ({ tag: node.tagName, className: node.className })),
    );
    expect(overflow).toEqual([]);
    await page.screenshot({
      path: fileURLToPath(new URL(`../../.local/home-${width}.png`, import.meta.url)),
    });
    await expect(page.getByRole("button", { name: "Tạo yêu cầu", exact: true })).toHaveCount(0);
  });
}
