import { expect, test, type Page } from "@playwright/test";
import { mockHome } from "./homeFixture";

const initial = () => ({
  processId: "lab-a-process",
  version: "a".repeat(64),
  editable: true,
  cancellable: true,
  cancelled: false,
  explanation: "Chỉ sửa hoặc hủy khi yêu cầu chưa được duyệt.",
  fields: [{ key: "room", label: "Phòng", value: "A101", maxLength: 50, multiline: false }],
  history: [] as {
    id: string;
    version: number;
    action: string;
    actor: string;
    reason: string;
    beforeValues: Record<string, string>;
    afterValues: Record<string, string>;
    createdAt: string;
  }[],
});

const openRoomRequest = async (page: Page, view: ReturnType<typeof initial>) => {
  await page.route(/\/instances\/[^/]+\/timeline$/, (route) =>
    route.fulfill({
      json: {
        id: view.processId,
        processKey: "room-request",
        businessKey: "lab-a-request",
        workspace: "lab-a",
        initiator: "home-user",
        startedAt: "2026-10-31T01:00:00Z",
        endedAt: null,
        currentTasks: [],
        steps: [],
        variables: { room: view.fields[0].value },
        cancelled: view.cancelled,
      },
    }),
  );
  await page.route("**/api/apps/roomrequest/v1/workspaces/lab-a/requests/lab-a-request", (route) =>
    route.fulfill({
      json: {
        id: "lab-a-request",
        workspace: "lab-a",
        creator: "home-user",
        room: view.fields[0].value,
        knowledgeId: null,
        process: {
          processInstanceId: view.processId,
          processKey: "room-request",
          businessKey: "lab-a-request",
          workspace: "lab-a",
        },
      },
    }),
  );
  await page.goto("/requests");
  await page.getByRole("link", { name: "lab-a-re", exact: true }).click();
  await page.getByRole("button", { name: "Sửa", exact: true }).click();
};

for (const width of [360, 1440]) {
  test(`mở từ danh sách rồi sửa và hủy đặt phòng ở ${width}px`, async ({ page }) => {
    await page.setViewportSize({ width, height: 960 });
    await mockHome(page, ["request:create"]);
    const view = initial();
    const writes: string[] = [];
    await page.route(/\/instances\/[^/]+\/request(?:\/cancel)?$/, async (route) => {
      const request = route.request();
      if (request.method() === "GET") return route.fulfill({ json: view });
      const payload = request.postDataJSON();
      expect(payload.expectedVersion).toBe(view.version);
      const cancelled = request.url().endsWith("/cancel");
      const before = { room: view.fields[0].value };
      if (!cancelled) view.fields[0].value = payload.values.room;
      view.history.unshift({
        id: `11111111-1111-4111-8111-11111111111${view.history.length}`,
        version: view.history.length + 1,
        action: cancelled ? "CANCELLED" : "UPDATED",
        actor: "home-user",
        reason: payload.reason,
        beforeValues: before,
        afterValues: { room: view.fields[0].value },
        createdAt: "2026-10-31T13:00:00Z",
      });
      view.version = (cancelled ? "c" : "b").repeat(64);
      view.cancelled = cancelled;
      view.editable = !cancelled;
      view.cancellable = !cancelled;
      writes.push(cancelled ? "cancel" : "update");
      return route.fulfill({ json: view });
    });
    await openRoomRequest(page, view);
    const dialog = page.getByRole("dialog");
    const box = await dialog.boundingBox();
    expect(box!.x).toBeGreaterThanOrEqual(0);
    expect(box!.x + box!.width).toBeLessThanOrEqual(width);
    await dialog.getByLabel("Phòng", { exact: true }).fill("B202");
    await dialog.getByLabel("Lý do", { exact: true }).fill("Đổi sang phòng có máy chiếu");
    await dialog.getByRole("button", { name: "Lưu thay đổi" }).click();
    await expect(page.getByRole("region", { name: "Lịch sử thay đổi" })).toContainText("Sau: B202");
    await expect(page.getByRole("region", { name: "Lịch sử thay đổi" })).toContainText(
      "Trước: A101",
    );
    await expect(page).toHaveURL(/\/roomrequest\/requests\/lab-a-request$/);
    await page.getByRole("button", { name: "Hủy", exact: true }).click();
    await dialog.getByLabel("Lý do", { exact: true }).fill("Không còn nhu cầu sử dụng phòng");
    await dialog.getByRole("button", { name: "Xác nhận hủy" }).click();
    await expect(page.getByRole("region", { name: "Lịch sử thay đổi" })).toContainText(
      "Đã hủy yêu cầu",
    );
    await expect(page.getByRole("button", { name: "Sửa", exact: true })).toHaveCount(0);
    await expect(page.getByRole("button", { name: "Hủy", exact: true })).toHaveCount(0);
    expect(writes).toEqual(["update", "cancel"]);
    await expect(dialog).toHaveCount(0);
  });
}

test("xung đột giữ nội dung đang nhập và cho tải lại phiên bản hiện tại", async ({ page }) => {
  await mockHome(page, ["request:create"]);
  const view = initial();
  await page.route(/\/instances\/[^/]+\/request$/, (route) => {
    if (route.request().method() === "GET") return route.fulfill({ json: view });
    view.editable = false;
    view.cancellable = false;
    view.version = "d".repeat(64);
    return route.fulfill({
      status: 409,
      json: { title: "Conflict", detail: "Request changed", requestId: "request-version-conflict" },
    });
  });
  await openRoomRequest(page, view);
  const dialog = page.getByRole("dialog");
  await dialog.getByLabel("Phòng", { exact: true }).fill("C303");
  await dialog.getByLabel("Lý do", { exact: true }).fill("Cập nhật phòng");
  await dialog.getByRole("button", { name: "Lưu thay đổi" }).click();
  await expect(dialog.getByRole("alert")).toContainText("request-version-conflict");
  await expect(dialog.getByLabel("Phòng", { exact: true })).toHaveValue("C303");
  await dialog.getByRole("button", { name: "Tải lại yêu cầu" }).click();
  await expect(dialog).toHaveCount(0);
  await expect(page.getByRole("button", { name: "Sửa", exact: true })).toHaveCount(0);
});
