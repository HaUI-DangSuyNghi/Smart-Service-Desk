import { expect, test } from "@playwright/test";
import { mockTicketPage } from "./ticketFixture";

const id = "11111111-1111-4111-8111-111111111111";
const path = `/servicedesk/workspaces/scope-a/tickets/${id}`;
const success = {
  contractVersion: "0.1.0",
  requestId: "22222222-2222-4222-8222-222222222222",
  status: "SUCCESS",
  answer: "Inspect the device connection. [C1]",
  citations: [{ citationId: "C1", displayTitle: "Synthetic guide", sourceVersion: "1" }],
  needsReview: true,
  reasonCode: "GROUNDED",
};

// Browser contract controls use a synthetic session and a stub public API.
// They are explicitly not Keycloak/gateway/Core/model production acceptance.
test.beforeEach(async ({ page }) => {
  await page.route("**/src/platform/human/auth/userManager.ts*", (route) => {
    return route.fulfill({
      contentType: "application/javascript",
      body: `
        const callbacks = new Map();
        const user = { profile: { sub: "synthetic-browser-actor", preferred_username: "synthetic" },
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
  // Trang chi tiết tải ticket độc lập với trợ lý; 404 để không kéo theo timeline
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

test("shows verified quotes and citations; sends only question", async ({ page }) => {
  let calls = 0;
  await page.route("**/api/apps/servicedesk/**/assistance", async (route) => {
    calls++;
    expect(route.request().postDataJSON()).toEqual({ question: "device connection" });
    await route.fulfill({ json: success });
  });

  await page.goto(path);
  await page.getByLabel("Bạn cần hỗ trợ điều gì?").fill("device connection");
  await page.getByRole("button", { name: "Tìm hướng dẫn" }).click();
  await expect(page.getByRole("blockquote")).toContainText("Inspect the device connection. [C1]");
  await expect(page.getByText("[C1] Synthetic guide")).toBeVisible();
  await expect(page.getByText("Cần kiểm tra trước khi áp dụng", { exact: false })).toBeVisible();
  expect(calls).toBe(1);
});

test("renders hostile HTML as plain text", async ({ page }) => {
  await page.route("**/api/apps/servicedesk/**/assistance", (route) =>
    route.fulfill({
      json: {
        ...success,
        answer: '<img src=x onerror="window.syntheticExecuted=true"> [C1]',
        citations: [{ ...success.citations[0], displayTitle: "<script>synthetic</script>" }],
      },
    }),
  );

  await page.goto(path);
  await page.getByLabel("Bạn cần hỗ trợ điều gì?").fill("device connection");
  await page.getByRole("button", { name: "Tìm hướng dẫn" }).click();
  await expect(page.getByRole("blockquote")).toContainText("<img");
  await expect(page.locator("blockquote img")).toHaveCount(0);
  await expect(page.getByText("<script>synthetic</script>", { exact: false })).toBeVisible();
});

test("explicit refusal has no quote or citations", async ({ page }) => {
  await page.route("**/api/apps/servicedesk/**/assistance", (route) =>
    route.fulfill({
      json: {
        ...success,
        status: "INSUFFICIENT_EVIDENCE",
        answer: null,
        citations: [],
        reasonCode: "INSUFFICIENT_EVIDENCE",
      },
    }),
  );

  await page.goto(path);
  await page.getByLabel("Bạn cần hỗ trợ điều gì?").fill("unanswerable question");
  await page.getByRole("button", { name: "Tìm hướng dẫn" }).click();
  await expect(
    page.getByRole("region", { name: "Trợ lý tri thức" }).getByRole("status"),
  ).toContainText("Chưa có đủ bằng chứng");
  await expect(page.getByRole("blockquote")).toHaveCount(0);
});

for (const status of [401, 403, 404, 409, 502, 503, 504]) {
  test(`HTTP ${status} clears old quote and keeps manual navigation available`, async ({
    page,
  }) => {
    let calls = 0;
    await page.route("**/api/apps/servicedesk/**/assistance", async (route) => {
      calls++;
      if (calls === 1) await route.fulfill({ json: success });
      else await route.fulfill({ status, json: { code: "SYNTHETIC_FAILURE" } });
    });

    await page.goto(path);
    await page.getByLabel("Bạn cần hỗ trợ điều gì?").fill("device connection");
    await page.getByRole("button", { name: "Tìm hướng dẫn" }).click();
    await expect(page.getByRole("blockquote")).toBeVisible();
    await page.getByRole("button", { name: "Tìm hướng dẫn" }).click();
    await expect(page.getByRole("blockquote")).toHaveCount(0);
    await expect(
      page.getByRole("region", { name: "Trợ lý tri thức" }).getByRole("status"),
    ).not.toBeEmpty();
    await expect(page.getByRole("button", { name: "Quay lại", exact: true })).toBeEnabled();
  });
}

test("route change and session expiry clear scoped results", async ({ page }) => {
  await page.route("**/api/apps/servicedesk/**/assistance", (route) =>
    route.fulfill({ json: success }),
  );
  await page.goto(path);
  await page.getByLabel("Bạn cần hỗ trợ điều gì?").fill("device connection");
  await page.getByRole("button", { name: "Tìm hướng dẫn" }).click();
  await expect(page.getByRole("blockquote")).toBeVisible();

  await page.evaluate(() => window.dispatchEvent(new Event("session-ended")));
  await expect(page.getByRole("blockquote")).toHaveCount(0);
  await expect(page.getByLabel("Bạn cần hỗ trợ điều gì?")).toHaveCount(0);

  await page.goto(path.replace(id, "33333333-3333-4333-8333-333333333333"));
  await expect(page.getByRole("blockquote")).toHaveCount(0);
});
