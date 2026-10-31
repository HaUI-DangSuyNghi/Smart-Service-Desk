import { expect, test, type Page } from "@playwright/test";
import { mockHome } from "./homeFixture";

const id = "11111111-1111-4111-8111-111111111111";
const otherId = "22222222-2222-4222-8222-222222222222";
const date = "2026-10-31T01:00:00Z";
const setup = async (page: Page, approved = false, board = false) => {
  await mockHome(page, ["request:create", ...(board ? ["task:handle"] : [])]);
  const writes: string[] = [];
  const records = [id, otherId].map((value, index) => ({
    id: value,
    workspace: "lab-a",
    creator: index === 0 ? "home-user" : "another-user",
    clientRequestId: value,
    title: index === 0 ? "Ticket của tôi" : "Ticket người khác",
    description: "Máy tính không kết nối được mạng",
    createdAt: date,
    state: approved ? "ASSIGNED" : "AWAITING_REVIEW",
    taskId: `task-${value}`,
    assignee: null,
    classification: null,
    process: {
      processInstanceId: `process-${value}`,
      processKey: "service-desk-intake-reviewed",
      businessKey: value,
      workspace: "lab-a",
    },
  }));
  const view = {
    processId: records[0].process.processInstanceId,
    version: "a".repeat(64),
    editable: !approved,
    cancellable: !approved,
    closable: approved,
    cancelled: false,
    explanation: "Chủ ticket sửa/hủy trước duyệt, đóng sau duyệt.",
    fields: [
      { key: "title", label: "Tiêu đề", value: records[0].title, maxLength: 200, multiline: false },
      {
        key: "description",
        label: "Mô tả",
        value: records[0].description,
        maxLength: 4000,
        multiline: true,
      },
    ],
    history: [
      {
        id: "33333333-3333-4333-8333-333333333333",
        version: 1,
        action: "UPDATED",
        actor: "home-user",
        reason: "Đính chính thiết bị",
        beforeValues: { title: "Máy 15", description: "Mô tả cũ" },
        afterValues: { title: "Máy 14", description: "Mô tả mới" },
        createdAt: "2026-10-31T02:00:00Z",
      },
    ],
  };
  await page.route(/\/api\/apps\/servicedesk\//, (route) => {
    const url = new URL(route.request().url());
    if (url.pathname.endsWith("/intelligence/status"))
      return route.fulfill({ json: { mode: "DISABLED", engine: "rules", modelEnabled: false } });
    if (url.pathname.endsWith("/context-revision"))
      return route.fulfill({
        json: {
          contractVersion: "ticket-context-revision@0.1.0",
          ticketId: id,
          contextRevision: 1,
        },
      });
    if (url.pathname.endsWith("/tickets")) {
      const states = url.searchParams.getAll("state").flatMap((value) => value.split(","));
      const items = records.filter((ticket) => !states.length || states.includes(ticket.state));
      return route.fulfill({ json: { items, total: items.length, page: 0, size: 20 } });
    }
    const ticket = records.find((record) => url.pathname.endsWith(`/tickets/${record.id}`));
    return ticket
      ? route.fulfill({ json: ticket })
      : route.fulfill({ status: 404, json: { title: "Not found" } });
  });
  await page.route(/\/instances\/[^/]+\/request(?:\/(?:cancel|close))?$/, async (route) => {
    if (route.request().method() === "GET") return route.fulfill({ json: view });
    const payload = route.request().postDataJSON();
    expect(payload.expectedVersion).toBe(view.version);
    const action =
      route.request().method() === "PATCH"
        ? "edit"
        : route.request().url().endsWith("/close")
          ? "close"
          : "cancel";
    writes.push(action);
    if (action === "edit") {
      records[0].title = payload.values.title;
      view.fields[0].value = payload.values.title;
    } else {
      records[0].state = action === "close" ? "CLOSED" : "CANCELLED";
      view.editable = false;
      view.cancellable = false;
      view.closable = false;
    }
    view.version = "b".repeat(64);
    return route.fulfill({ json: view });
  });
  await page.route(/\/instances\/[^/]+\/timeline$/, (route) =>
    route.fulfill({
      json: {
        id: records[0].process.processInstanceId,
        processKey: "service-desk-intake-reviewed",
        businessKey: id,
        workspace: "lab-a",
        initiator: "home-user",
        startedAt: date,
        endedAt: null,
        variables: {},
        currentTasks: [],
        steps: [],
      },
    }),
  );
  return { writes };
};

for (const width of [360, 1440]) {
  test(`bảng và chi tiết dùng Sửa/Hủy của chủ ticket, có xác nhận ở ${width}px`, async ({
    page,
  }) => {
    await page.setViewportSize({ width, height: 960 });
    const { writes } = await setup(page);
    await page.goto("/servicedesk/tickets");
    const mine = page.getByRole("row").filter({ hasText: "Ticket của tôi" });
    const other = page.getByRole("row").filter({ hasText: "Ticket người khác" });
    await expect(mine.getByRole("button", { name: "Sửa", exact: true })).toBeVisible();
    await expect(other.getByRole("button", { name: "Sửa", exact: true })).toHaveCount(0);
    await expect(other.getByRole("button", { name: "Hủy", exact: true })).toHaveCount(0);
    await expect(page.getByRole("button", { name: "Bổ sung thông tin", exact: true })).toHaveCount(
      0,
    );
    await mine.getByRole("button", { name: "Sửa", exact: true }).click();
    await page.getByRole("dialog").getByLabel("Tiêu đề", { exact: true }).fill("Máy in bị lỗi");
    await page
      .getByRole("dialog")
      .getByLabel("Lý do", { exact: true })
      .fill("Sửa nội dung gửi nhầm");
    await page.getByRole("button", { name: "Lưu thay đổi" }).click();
    await expect(page.getByRole("dialog")).toHaveCount(0);
    await expect(page).toHaveURL(/\/servicedesk\/tickets$/);
    await page.goto(`/servicedesk/tickets/${id}`);
    await expect(page.getByRole("button", { name: "Quản lý yêu cầu" })).toHaveCount(0);
    await expect(page.getByRole("button", { name: "Sửa", exact: true })).toBeVisible();
    await page.getByRole("button", { name: "Hủy", exact: true }).click();
    await expect(page.getByRole("dialog")).toContainText("dừng các công việc còn mở");
    expect(writes).toEqual(["edit"]);
    await page.getByRole("dialog").getByLabel("Lý do", { exact: true }).fill("Không còn nhu cầu");
    await page.getByRole("button", { name: "Xác nhận hủy" }).click();
    await expect(page.getByRole("dialog")).toHaveCount(0);
    expect(writes).toEqual(["edit", "cancel"]);
  });
}

test("sau duyệt chỉ chủ ticket có Đóng và cần xác nhận", async ({ page }) => {
  const { writes } = await setup(page, true);
  await page.goto("/servicedesk/tickets");
  const mine = page.getByRole("row").filter({ hasText: "Ticket của tôi" });
  const other = page.getByRole("row").filter({ hasText: "Ticket người khác" });
  await expect(mine.getByRole("button", { name: "Sửa", exact: true })).toHaveCount(0);
  await expect(mine.getByRole("button", { name: "Hủy", exact: true })).toHaveCount(0);
  await expect(other.getByRole("button", { name: "Đóng", exact: true })).toHaveCount(0);
  await mine.getByRole("button", { name: "Đóng", exact: true }).click();
  expect(writes).toEqual([]);
  await expect(page.getByRole("button", { name: "Xác nhận đóng" })).toBeDisabled();
  await page.getByRole("dialog").getByLabel("Lý do", { exact: true }).fill("Không cần xử lý tiếp");
  await page.getByRole("button", { name: "Xác nhận đóng" }).click();
  await expect(page.getByRole("dialog")).toHaveCount(0);
  expect(writes).toEqual(["close"]);
});

test("menu card có Sửa/Hủy và tiến độ có nội dung trước/sau", async ({ page }) => {
  await setup(page, false, true);
  await page.goto("/servicedesk/tickets");
  await page.getByRole("tab", { name: "Board", exact: true }).click();
  const mine = page.getByRole("button", { name: "Ticket của tôi, mở chi tiết", exact: true });
  await mine.getByRole("button", { name: "Thao tác với ticket" }).click();
  await expect(page.getByRole("menuitem", { name: "Sửa", exact: true })).toBeVisible();
  await page.getByRole("menuitem", { name: "Hủy", exact: true }).click();
  await expect(page.getByRole("dialog", { name: "Hủy yêu cầu" })).toBeVisible();
  await page.getByRole("button", { name: "Quay lại" }).click();
  await page.goto(`/servicedesk/tickets/${id}`);
  await page.getByRole("button", { name: "Tiến độ", exact: true }).click();
  await expect(page.getByText("Đính chính thiết bị", { exact: true })).toBeVisible();
  await page.getByText("Nội dung đã sửa", { exact: true }).click();
  await expect(page.getByText("Trước: Máy 15", { exact: true })).toBeVisible();
  await expect(page.getByText("Sau: Máy 14", { exact: true })).toBeVisible();
});
