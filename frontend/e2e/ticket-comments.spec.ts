import { expect, test } from "@playwright/test";
import { mockTicketPage } from "./ticketFixture";

const id = "11111111-1111-4111-8111-111111111111";
const path = `/servicedesk/workspaces/scope-a/tickets/${id}`;
const endpoint = `**/api/apps/servicedesk/**/tickets/${id}/comments`;
const comment = {
  id: "22222222-2222-4222-8222-222222222222",
  author: "synthetic-browser-actor",
  body: "Private ticket comment",
  createdAt: "2026-11-07T00:00:00Z",
};

test.beforeEach(async ({ page }) => {
  await page.route(/\/api\/(?:hpdi|core|apps)\//, (route) =>
    route.fulfill({ status: 404, json: { title: "Not Found" } }),
  );
  await page.routeWebSocket("**/ws", () => {});
  await page.route("**/src/platform/human/auth/userManager.ts*", (route) => {
    return route.fulfill({
      contentType: "application/javascript",
      body: `
        const callbacks = new Map();
        let user = { profile: { sub: "synthetic-browser-actor", preferred_username: "synthetic" },
          expired: false, access_token: "synthetic-browser-contract-token" };
        const events = new Proxy({}, { get: (_, name) => (callback) => {
          const key = String(name).replace(/^(add|remove)/, "");
          if (!callbacks.has(key)) callbacks.set(key, new Set());
          if (String(name).startsWith("add")) callbacks.get(key).add(callback);
          else callbacks.get(key).delete(callback);
        }});
        window.addEventListener("session-ended", () => {
          for (const callback of callbacks.get("UserUnloaded") ?? []) callback();
        });
        window.addEventListener("session-changed", () => {
          user = { ...user, access_token: "second-browser-token", profile: { ...user.profile, sub: "second-browser-actor" } };
          for (const callback of callbacks.get("UserLoaded") ?? []) callback(user);
        });
        export const getUserManager = () => ({ events, getUser: async () => user,
          signinRedirect: async () => undefined, signoutRedirect: async () => undefined });
        export const restoreSession = async () => user;
        export const currentAccessToken = async () => user.access_token;
        export const renewAccessToken = async () => null;
      `,
    });
  });
  await page.route("**/api/hpdi/v1/workspaces", (route) =>
    route.fulfill({
      json: [{ workspace: "scope-a", name: "Synthetic A", roles: ["requester"] }],
    }),
  );
  await page.route("**/api/hpdi/v1/workspaces/*/actor", (route) =>
    route.fulfill({
      json: {
        subject: "synthetic-browser-actor",
        workspace: "scope-a",
        roles: ["requester"],
        capabilities: [],
      },
    }),
  );
  await mockTicketPage(page, id, "synthetic-browser-actor");
  await page.route("**/api/core/v1/me", (route) =>
    route.fulfill({
      json: {
        sub: "synthetic-browser-actor",
        username: "synthetic",
        memberships: [{ workspace: "scope-a", role: "requester" }],
        platformAdmin: false,
      },
    }),
  );
});

test("shows comments instead of the retired ticket assistance form", async ({ page }) => {
  const comments = [comment];
  await page.route(endpoint, async (route) => {
    if (route.request().method() === "POST") {
      expect(route.request().postDataJSON()).toEqual({ body: "New comment" });
      const added = { ...comment, id: "33333333-3333-4333-8333-333333333333", body: "New comment" };
      comments.push(added);
      await route.fulfill({ status: 201, json: added });
    } else await route.fulfill({ json: comments });
  });
  await page.goto(path);
  await expect(page.getByText(comment.body, { exact: true })).toBeVisible();
  await expect(page.getByRole("heading", { name: "Bình luận", exact: true })).toBeVisible();
  await expect(page.getByRole("button", { name: "Tìm hướng dẫn" })).toHaveCount(0);
  await page.getByRole("button", { name: "Thêm bình luận..." }).click();
  await page.getByRole("textbox", { name: "Bình luận", exact: true }).fill("  New comment  ");
  await page.getByRole("button", { name: "Lưu", exact: true }).click();
  await expect(page.getByText("New comment", { exact: true })).toBeVisible();
  await expect(page.getByRole("textbox", { name: "Bình luận", exact: true })).toHaveCount(0);
});

test("renders hostile comment markup as plain text", async ({ page }) => {
  const body = '<img src=x onerror="window.syntheticExecuted=true">';
  await page.route(endpoint, (route) => route.fulfill({ json: [{ ...comment, body }] }));
  await page.goto(path);
  await expect(page.getByText(body, { exact: true })).toBeVisible();
  expect(await page.evaluate(() => Reflect.get(window, "syntheticExecuted"))).toBeUndefined();
  await expect(page.locator('img[src="x"]')).toHaveCount(0);
});

for (const status of [401, 403, 404, 409, 502, 503, 504]) {
  test(`HTTP ${status} preserves an unsent comment for explicit retry`, async ({ page }) => {
    let submissions = 0;
    await page.route(endpoint, (route) => {
      if (route.request().method() === "POST") {
        submissions++;
        return route.fulfill({ status, json: { code: "SYNTHETIC_FAILURE" } });
      }
      return route.fulfill({ json: [] });
    });
    await page.goto(path);
    await page.getByRole("button", { name: "Thêm bình luận..." }).click();
    const input = page.getByRole("textbox", { name: "Bình luận", exact: true });
    await input.fill("Keep this draft");
    await page.getByRole("button", { name: "Lưu", exact: true }).click();
    await expect(page.getByText("Không gửi được bình luận.", { exact: true })).toBeVisible();
    await expect(input).toHaveValue("Keep this draft");
    await expect(page.getByRole("button", { name: "Lưu", exact: true })).toBeEnabled();
    await expect(page.getByRole("button", { name: "Quay lại", exact: true })).toBeEnabled();
    expect(submissions).toBe(1);
  });
}

test("session expiry removes private comments and the editor", async ({ page }) => {
  await page.route(endpoint, (route) => route.fulfill({ json: [comment] }));
  await page.goto(path);
  await expect(page.getByText(comment.body, { exact: true })).toBeVisible();
  await page.getByRole("button", { name: "Thêm bình luận..." }).click();
  await page.getByRole("textbox", { name: "Bình luận", exact: true }).fill("Private draft");
  await page.evaluate(() => window.dispatchEvent(new Event("session-ended")));
  await expect(page.getByText(comment.body, { exact: true })).toHaveCount(0);
  await expect(page.getByRole("textbox", { name: "Bình luận", exact: true })).toHaveCount(0);
});

test("identity change clears cached comments before the new response", async ({ page }) => {
  let changed = false;
  let release!: () => void;
  const response = new Promise<void>((resolve) => {
    release = resolve;
  });
  await page.route(endpoint, async (route) => {
    if (changed) await response;
    await route.fulfill({ json: changed ? [] : [comment] });
  });
  await page.goto(path);
  await expect(page.getByText(comment.body, { exact: true })).toBeVisible();
  changed = true;
  try {
    await page.evaluate(() => window.dispatchEvent(new Event("session-changed")));
    await expect(page.getByText(comment.body, { exact: true })).toHaveCount(0);
  } finally {
    release();
  }
  await expect(page.getByText("Chưa có bình luận", { exact: true })).toBeVisible();
});
