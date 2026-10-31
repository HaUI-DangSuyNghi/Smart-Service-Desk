import { expect, test } from "@playwright/test";
import { fileURLToPath } from "node:url";

const id = "11111111-1111-4111-8111-111111111111";
const source = {
  index: 1,
  metadataId: id,
  title: "Hướng dẫn kết nối mạng",
  version: "1",
  excerpt: "Kiểm tra dây cáp mạng trước khi khởi động lại router.",
};

test.beforeEach(async ({ page }) => {
  await page.route("**/src/platform/human/auth/userManager.ts*", (route) =>
    route.fulfill({
      contentType: "application/javascript",
      body: `
      const user = {profile: {sub: "synthetic-reader", preferred_username: "reader"}, expired: false, access_token: "synthetic-token"};
      export const getUserManager = () => ({events: new Proxy({}, {get: () => () => {}}), getUser: async () => user, signinRedirect: async () => {}, signoutRedirect: async () => {}});
      export const restoreSession = async () => user;
      export const currentAccessToken = async () => user.access_token;
      export const renewAccessToken = async () => null;
    `,
    }),
  );
  await page.route(/\/api\/(?:hpdi|core|apps)\//, (route) => {
    const path = new URL(route.request().url()).pathname;
    if (path.endsWith("/workspaces"))
      return route.fulfill({
        json: [{ workspace: "lab-a", name: "Phòng máy A", roles: ["requester"] }],
      });
    if (path.endsWith("/permissions"))
      return route.fulfill({
        json: [
          { key: "document:view", label: "Xem tài liệu" },
          { key: "request:create", label: "Tạo yêu cầu" },
        ],
      });
    if (path.endsWith("/actor"))
      return route.fulfill({
        json: {
          subject: "synthetic-reader",
          workspace: "lab-a",
          roles: ["requester"],
          capabilities: [],
        },
      });
    if (path.endsWith("/me"))
      return route.fulfill({
        json: {
          sub: "synthetic-reader",
          username: "reader",
          memberships: [{ workspace: "lab-a", role: "requester" }],
          platformAdmin: false,
        },
      });
    if (path.endsWith(`/knowledge/${id}/record`))
      return route.fulfill({
        json: {
          id,
          title: source.title,
          version: "1",
          fileId: id,
          source: "Internal guide",
          usageRights: "Internal",
          checksum: "a".repeat(64),
          owner: "reader",
          ownerName: "Reader",
          createdAt: "2026-10-30T00:00:00Z",
        },
      });
    if (path.endsWith(`/knowledge/${id}`))
      return route.fulfill({
        json: {
          metadataId: id,
          title: source.title,
          version: "1",
          mediaType: "text/plain",
          text: source.excerpt,
        },
      });
    if (path.endsWith("/knowledge"))
      return route.fulfill({
        json: { items: [{ id, title: source.title, version: "1" }], page: 0, size: 100, total: 1 },
      });
    return route.fulfill({ status: 404, json: { title: "Not found" } });
  });
});

test("requester can chat, follow citations and preview without download", async ({ page }) => {
  await page.route("**/knowledge/chat", (route) => {
    expect(route.request().postDataJSON()).toEqual({
      question: "Làm gì khi mất mạng?",
      documentIds: [],
    });
    return route.fulfill({
      contentType: "text/event-stream",
      body: [
        { type: "sources", sources: [source] },
        { type: "delta", text: "Kiểm tra dây cáp mạng " },
        { type: "delta", text: "[1]." },
        { type: "done", grounded: true },
      ]
        .map((event) => `data: ${JSON.stringify(event)}\n\n`)
        .join(""),
    });
  });
  await page.goto("/knowledge-chat");
  await expect(page.getByRole("heading", { name: "Hỏi đáp tài liệu", exact: true })).toBeVisible();
  await page.getByLabel("Câu hỏi về tài liệu").fill("Làm gì khi mất mạng?");
  await page.getByRole("button", { name: "Gửi câu hỏi" }).click();
  await expect(page.getByText("Có nguồn đối chiếu.", { exact: false })).toBeVisible();
  await page.getByRole("button", { name: `Đọc nguồn 1: ${source.title}` }).click();
  await expect(page.getByRole("dialog")).toContainText(source.excerpt);
  await expect(page.getByRole("dialog").getByRole("button", { name: "Tải về" })).toHaveCount(0);
  await page.getByRole("dialog").getByRole("button", { name: "Close" }).click();
  await page.screenshot({
    path: fileURLToPath(new URL("../../.local/knowledge-chat-desktop.png", import.meta.url)),
  });
});

test("completed answer hides unused sources and scrolls preview to cited passage", async ({ page }) => {
  const unused = { ...source, title: "Unused candidate" };
  const cited = { ...source, index: 2 };
  await page.route(`**/knowledge/${id}`, (route) => route.fulfill({
    json: { metadataId: id, title: source.title, version: "1", mediaType: "text/plain",
      text: "Earlier unrelated content.\n".repeat(180) + source.excerpt },
  }));
  await page.route("**/knowledge/chat", (route) => route.fulfill({
    contentType: "text/event-stream",
    body: [
      { type: "sources", sources: [unused, cited] },
      { type: "delta", text: "Check the cable [2]." },
      { type: "done", grounded: true },
    ].map((event) => `data: ${JSON.stringify(event)}\n\n`).join(""),
  }));
  await page.goto("/knowledge-chat");
  await page.getByLabel("Câu hỏi về tài liệu").fill("Cable?");
  await page.getByRole("button", { name: "Gửi câu hỏi" }).click();
  await expect(page.getByText("Có nguồn đối chiếu.", { exact: false })).toBeVisible();
  await expect(page.getByText("Unused candidate", { exact: false })).toHaveCount(0);
  await page.getByRole("button", { name: `Đọc nguồn 2: ${source.title}` }).click();
  await expect(page.getByRole("dialog").locator("mark")).toHaveText(source.excerpt);
  await expect(page.getByRole("dialog").locator("mark")).toBeInViewport();
});

test("preview page hides original download for requester", async ({ page }) => {
  await page.goto(`/knowledge/${id}`);
  await expect(page.getByText(source.excerpt, { exact: true })).toBeVisible();
  await expect(page.getByRole("button", { name: "Tải về" })).toHaveCount(0);
});

test("mobile handles failed streams and renders hostile markup as text", async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await page.route("**/knowledge/chat", (route) =>
    route.fulfill({
      contentType: "text/event-stream",
      body: [
        { type: "sources", sources: [source] },
        { type: "delta", text: '<img src=x onerror="alert(1)"> [1]' },
        { type: "error", code: "KNOWLEDGE_UNAVAILABLE" },
      ]
        .map((event) => `data: ${JSON.stringify(event)}\n\n`)
        .join(""),
    }),
  );
  await page.goto("/knowledge-chat");
  await page.getByLabel("Câu hỏi về tài liệu").fill("Mạng lỗi");
  await page.getByRole("button", { name: "Gửi câu hỏi" }).click();
  await expect(page.getByRole("alert")).toContainText("chưa được kiểm tra hoàn tất");
  await expect(page.locator('[role="log"] img')).toHaveCount(0);
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(
    true,
  );
  await page.screenshot({
    path: fileURLToPath(new URL("../../.local/knowledge-chat-mobile.png", import.meta.url)),
  });
});
