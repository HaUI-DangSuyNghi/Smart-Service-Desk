import { expect, test, type Page } from "@playwright/test";
import { mockTicketPage, ticketFixture } from "./ticketFixture";
const id = "11111111-1111-4111-8111-111111111111";
const path = `/servicedesk/workspaces/scope-a/tickets/${id}`;
const session = async (page: Page) => {
  await page.route("**/src/platform/human/auth/userManager.ts*", (route) =>
    route.fulfill({
      contentType: "application/javascript",
      body: `
      const user = {profile:{sub:"synthetic-enrichment-user"},expired:false,access_token:"synthetic"};
      export const getUserManager=()=>({events:new Proxy({}, {get:()=>()=>{}}),getUser:async()=>user,signinRedirect:async()=>{},signoutRedirect:async()=>{}});
      export const restoreSession=async()=>user;
      export const currentAccessToken=async()=>user.access_token;
      export const renewAccessToken=async()=>null;`,
    }),
  );
  await page.route("**/api/hpdi/v1/workspaces", (route) =>
    route.fulfill({ json: [{ workspace: "scope-a", name: "Synthetic A", roles: ["requester"] }] }),
  );
  await page.route("**/api/hpdi/v1/workspaces/*/actor", (route) =>
    route.fulfill({
      json: {
        subject: "synthetic-enrichment-user",
        workspace: "scope-a",
        roles: ["requester"],
        capabilities: [],
      },
    }),
  );
  await page.route("**/api/core/v1/me", (route) =>
    route.fulfill({
      json: {
        sub: "synthetic-enrichment-user",
        username: "synthetic",
        memberships: [{ workspace: "scope-a", role: "requester" }],
        platformAdmin: false,
      },
    }),
  );
  await mockTicketPage(page, id, "synthetic-enrichment-user");
  await page.route("**/api/hpdi/v1/workspaces/*/permissions", (route) =>
    route.fulfill({
      json: [
        { key: "task:assign", label: "Assign tasks" },
        { key: "task:handle", label: "Handle tasks" },
      ],
    }),
  );
};

const intake = {
  contractVersion: "intake-admission@1.0.0",
  ticketId: id,
  contextRevision: 2,
  version: 4,
  reviewRound: 1,
  status: "REVIEW_RELEASED",
  deadlineAt: "2026-10-27T12:00:00Z",
  lateNotice: false,
  reviewTaskMayExist: false,
  operationalAuthority: false,
  readiness: null,
  finalAssessment: { ai07: { category: "HARDWARE", priority: "HIGH", confidence: null } },
};

test("reads saved final assessment across reloads without any inference POST", async ({ page }) => {
  await session(page);
  let commands = 0;
  await page.route("**/api/apps/servicedesk/**", (route) => {
    if (route.request().method() === "POST") commands++;
    const url = new URL(route.request().url());
    if (url.pathname.endsWith(`/tickets/${id}`))
      return route.fulfill({ json: ticketFixture(id, "synthetic-enrichment-user") });
    if (url.pathname.endsWith("/intake")) return route.fulfill({ json: intake });
    if (url.pathname.endsWith("/estimate-default"))
      return route.fulfill({ json: { minutes: 90, basis: "DEFAULT" } });
    if (url.pathname.endsWith("/context-revision"))
      return route.fulfill({
        json: {
          contractVersion: "ticket-context-revision@0.1.0",
          ticketId: id,
          contextRevision: 2,
        },
      });
    return route.fulfill({ status: 404, json: { title: "Not Found" } });
  });
  for (let index = 0; index < 2; index++) {
    await page.goto(path);
    await expect(page.getByRole("heading", { name: "Gợi ý của AI", exact: false })).toBeVisible();
    await expect(page.getByRole("button", { name: "Chuyển sang duyệt thủ công" })).toHaveCount(0);
    await expect(page.getByRole("button", { name: "Đánh giá từ thông tin hiện tại" })).toHaveCount(
      0,
    );
  }
  expect(commands).toBe(0);
});

test("coordinator releases the current revision then reviews with updated CAS", async ({
  page,
}) => {
  await session(page);
  await page.route("**/api/hpdi/v1/workspaces/*/actor", (route) =>
    route.fulfill({
      json: {
        subject: "synthetic-enrichment-user",
        workspace: "scope-a",
        roles: ["coordinator"],
        capabilities: [],
      },
    }),
  );
  let released = false;
  let reviewed = false;
  await page.route("**/api/apps/servicedesk/**", async (route) => {
    const url = new URL(route.request().url());
    if (url.pathname.endsWith("/intake/manual-release")) {
      expect(route.request().postDataJSON()).toMatchObject({
        expectedContextRevision: 2,
        expectedVersion: 4,
        reason: "Synthetic intake review",
      });
      released = true;
      return route.fulfill({ json: { ...intake, version: 6, reviewTaskMayExist: true } });
    }
    if (url.pathname.endsWith("/review")) {
      expect(route.request().postDataJSON()).toMatchObject({
        expectedContextRevision: 2,
        expectedIntakeVersion: 6,
        category: "HARDWARE",
        priority: "MEDIUM",
      });
      reviewed = true;
      return route.fulfill({ json: {} });
    }
    if (url.pathname.endsWith("/intake"))
      return route.fulfill({
        json: {
          ...intake,
          status: reviewed ? "REVIEWED" : intake.status,
          version: released ? 6 : 4,
          reviewTaskMayExist: released,
        },
      });
    if (url.pathname.endsWith("/assignment-suggestion"))
      return route.fulfill({
        json: {
          taskId: "synthetic-review-task",
          group: "hardware",
          estimateMinutes: 90,
          recommended: null,
          reason: "NO_ON_DUTY_AGENT",
          candidates: [],
        },
      });
    if (url.pathname.endsWith("/estimate-default"))
      return route.fulfill({ json: { minutes: 90, basis: "DEFAULT" } });
    if (url.pathname.endsWith("/context-revision"))
      return route.fulfill({
        json: {
          contractVersion: "ticket-context-revision@0.1.0",
          ticketId: id,
          contextRevision: 2,
        },
      });
    if (url.pathname.endsWith(id))
      return route.fulfill({
        json: {
          id,
          workspace: "scope-a",
          creator: "another-requester",
          state: reviewed ? "ASSIGNED" : released ? "AWAITING_REVIEW" : "INTAKE",
          taskId: released ? "synthetic-review-task" : null,
          title: "Synthetic intake",
          description: "Synthetic",
          createdAt: "2026-10-27T00:00:00Z",
          process: {
            processInstanceId: "synthetic-process",
            processKey: "service-desk-intake-reviewed",
            businessKey: id,
            workspace: "scope-a",
          },
        },
      });
    return route.fulfill({ status: 404, json: { title: "Not Found" } });
  });
  await page.goto(path);
  await page.getByRole("button", { name: "Tiếp nhận", exact: true }).click();
  await page
    .getByRole("dialog")
    .getByLabel("Lý do", { exact: true })
    .fill("Synthetic intake review");
  await page.getByRole("dialog").getByRole("button", { name: "Gửi", exact: true }).click();
  await page.getByRole("button", { name: "Duyệt và giao việc", exact: true }).click();
  const review = page.getByRole("dialog");
  await review.locator('input[name="priority"][value="MEDIUM"]').check();
  await review.getByRole("radio", { name: "Để chờ ca sau (chưa giao ai)", exact: true }).check();
  await review.getByLabel("Lý do duyệt").fill("Synthetic review differs from AI");
  await review.getByRole("button", { name: "Duyệt và giao việc", exact: true }).click();
  await expect(review).toHaveCount(0);
  expect(released && reviewed).toBe(true);
});
