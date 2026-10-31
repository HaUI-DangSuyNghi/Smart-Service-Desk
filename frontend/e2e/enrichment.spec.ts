import { expect, test, type Page } from "@playwright/test";
import { mockTicketPage, ticketFixture } from "./ticketFixture";

const id = "11111111-1111-4111-8111-111111111111";
const path = `/servicedesk/workspaces/scope-a/tickets/${id}`;
const question = {
  questionId: "a".repeat(24),
  slot: "device.identifier",
  text: "Máy gặp lỗi có số nào?",
  choices: [],
  correctionRequired: false,
};

const state = (revision: number, ask = revision === 0) => ({
  contractVersion: "ticket-enrichment-lifecycle@0.1.0",
  ticketId: id,
  contextRevision: revision,
  status: ask ? "AWAITING_ANSWER" : "READY",
  version: revision + 1,
  roundCount: 1,
  manualReviewRequired: false,
  workflowGateApplied: false,
  needsReview: true,
  operationalAuthority: false,
  deadlineAt: null,
  remindedAt: null,
  requestInstanceId: ask ? "22222222-2222-4222-8222-222222222222" : null,
  deliveryStatus: ask ? "PENDING" : "NOT_REQUESTED",
  nextAction: ask ? "ANSWER_CLARIFICATION" : "CONTINUE_ADVISORY",
  reasonCodes: [],
  questions: ask ? [question] : [],
  readiness: {
    contractVersion: "ticket-readiness@0.1.0",
    ticketId: id,
    contextRevision: revision,
    contextDigest: "b".repeat(64),
    overall: ask ? "ENRICHMENT_REQUIRED" : "READINESS_OK",
    tasks: [],
    reasonCodes: [],
    nextAction: "ANSWER_CLARIFICATION",
    questions: ask ? [question] : [],
    policyDigest: "c".repeat(64),
    needsReview: true,
    operationalAuthority: false,
    workflowGateApplied: false,
  },
});

// Synthetic session + stub API: browser contract controls, không phải production/model acceptance.
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
};

test.beforeEach(async ({ page }) => {
  await session(page);
});

const revisionReply = (revision: number) => ({
  contractVersion: "ticket-context-revision@0.1.0",
  ticketId: id,
  contextRevision: revision,
});
const saved = (revision: number, replayed = false) => ({
  contractVersion: "ticket-clarification@0.1.0",
  ticketId: id,
  answerId: "33333333-3333-4333-8333-333333333333",
  contextRevision: revision,
  assessmentStatus: "AI07_REFRESH_REQUIRED",
  replayed,
});

test("answers on the existing ticket without claiming workflow pause", async ({ page }) => {
  let revision = 0;
  await page.route("**/context-revision", (route) =>
    route.fulfill({ json: revisionReply(revision) }),
  );
  await page.route("**/enrichment-state", (route) => route.fulfill({ json: state(revision) }));
  await page.route("**/clarifications", async (route) => {
    const input = route.request().postDataJSON();
    expect(input.expectedContextRevision).toBe(0);
    expect(input.answers).toEqual([{ slot: "device.identifier", value: "15", correction: false }]);
    expect(Object.keys(input).sort()).toEqual([
      "answers",
      "clientAnswerId",
      "contractVersion",
      "expectedContextRevision",
    ]);
    revision = 1;
    await route.fulfill({ status: 201, json: saved(revision) });
  });
  await page.goto(path);
  await page.getByRole("button", { name: "Bổ sung thông tin", exact: true }).click();
  await expect(page.getByLabel(question.text, { exact: true })).toBeVisible();
  await page.getByLabel(question.text, { exact: true }).fill("15");
  await page.getByRole("button", { name: "Gửi bổ sung", exact: true }).click();
  await expect(page.getByText("Đã lưu bổ sung vào ticket này.", { exact: false })).toBeVisible();
  await expect(page.getByText("Đã đủ thông tin để xem xét")).toBeVisible();
});

test("lost response keeps draft and replays the same clientAnswerId", async ({ page }) => {
  let revision = 0;
  const requests: { clientAnswerId: string; expectedContextRevision: number }[] = [];
  await page.route("**/context-revision", (route) =>
    route.fulfill({ json: revisionReply(revision) }),
  );
  await page.route("**/enrichment-state", (route) =>
    route.fulfill({ json: state(revision, true) }),
  );
  await page.route("**/clarifications", async (route) => {
    requests.push(route.request().postDataJSON());
    revision = 1;
    if (requests.length === 1) return route.abort("failed");
    await route.fulfill({ json: saved(1, true) });
  });
  await page.goto(path);
  await page.getByRole("button", { name: "Bổ sung thông tin", exact: true }).click();
  await page.getByLabel(question.text, { exact: true }).fill("15");
  await page.getByRole("button", { name: "Gửi bổ sung", exact: true }).click();
  await expect(page.getByText("Chưa xác nhận được việc lưu.", { exact: false })).toBeVisible();
  await expect(page.getByLabel(question.text, { exact: true })).toHaveValue("15");
  await page.evaluate(() => document.dispatchEvent(new Event("visibilitychange")));
  await page.getByRole("button", { name: "Gửi bổ sung", exact: true }).click();
  await expect(page.getByText("Đã lưu bổ sung vào ticket này.", { exact: false })).toBeVisible();
  expect(requests).toHaveLength(2);
  expect(requests[0].clientAnswerId).toBe(requests[1].clientAnswerId);
  expect(requests[1].expectedContextRevision).toBe(0);
});

test("409 refreshes revision but retains unsent content for an explicit retry", async ({
  page,
}) => {
  let revision = 0;
  const requests: { clientAnswerId: string; expectedContextRevision: number }[] = [];
  await page.route("**/context-revision", (route) =>
    route.fulfill({ json: revisionReply(revision) }),
  );
  await page.route("**/enrichment-state", (route) =>
    route.fulfill({ json: state(revision, true) }),
  );
  await page.route("**/clarifications", async (route) => {
    requests.push(route.request().postDataJSON());
    if (requests.length === 1) {
      revision = 1;
      return route.fulfill({ status: 409, json: { title: "Conflict", code: "CONFLICT" } });
    }
    revision = 2;
    return route.fulfill({ status: 201, json: saved(revision) });
  });
  await page.goto(path);
  await page.getByRole("button", { name: "Bổ sung thông tin", exact: true }).click();
  await page.getByLabel(question.text, { exact: true }).fill("15");
  await page.getByRole("button", { name: "Gửi bổ sung", exact: true }).click();
  await expect(page.getByText("Nội dung của bạn vẫn được giữ", { exact: false })).toBeVisible();
  await expect(page.getByLabel(question.text, { exact: true })).toHaveValue("15");
  await page.getByRole("button", { name: "Gửi bổ sung", exact: true }).click();
  await expect(page.getByText("Đã lưu bổ sung vào ticket này.", { exact: false })).toBeVisible();
  expect(requests[1].expectedContextRevision).toBe(1);
  expect(requests[1].clientAnswerId).not.toBe(requests[0].clientAnswerId);
});

test("forbidden lifecycle never displays hidden question text", async ({ page }) => {
  await page.route("**/context-revision", (route) => route.fulfill({ json: revisionReply(0) }));
  await page.route("**/enrichment-state", (route) =>
    route.fulfill({
      status: 403,
      json: { ...state(0), questions: [{ ...question, text: "hidden-other-owner-question" }] },
    }),
  );
  await page.goto(path);
  await page.getByRole("button", { name: "Bổ sung thông tin", exact: true }).click();
  await expect(
    page.getByText("Chưa tải được trạng thái kiểm tra.", { exact: false }),
  ).toBeVisible();
  await expect(page.getByText("hidden-other-owner-question")).toHaveCount(0);
});

test("small screen submits required correction and keeps the form usable", async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 });
  let revision = 0;
  await page.route("**/context-revision", (route) =>
    route.fulfill({ json: revisionReply(revision) }),
  );
  await page.route("**/enrichment-state", (route) => {
    const value = state(revision);
    if (value.questions[0])
      value.questions[0] = { ...value.questions[0], correctionRequired: true };
    return route.fulfill({ json: value });
  });
  await page.route("**/clarifications", async (route) => {
    expect(route.request().postDataJSON().answers[0].correction).toBe(true);
    revision = 1;
    await route.fulfill({ status: 201, json: saved(1) });
  });
  await page.goto(path);
  await page.getByRole("button", { name: "Bổ sung thông tin", exact: true }).click();
  await page.getByLabel(question.text, { exact: true }).fill("15");
  await page.getByRole("button", { name: "Gửi bổ sung", exact: true }).click();
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(
    true,
  );
  await page.screenshot({ path: "../.local/ai08-05/enrichment-mobile.png", fullPage: true });
  await expect(page.getByText("Đã lưu bổ sung vào ticket này.", { exact: false })).toBeVisible();
});

test("coordinator can find a timed-out ticket without claiming a workflow hold", async ({
  page,
}) => {
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
  await page.route("**/api/hpdi/v1/workspaces/*/permissions", (route) =>
    route.fulfill({ json: [{ key: "task:assign", label: "Assign tasks" }] }),
  );
  const nextId = "44444444-4444-4444-8444-444444444444";
  await page.route("**/api/apps/servicedesk/v1/workspaces/*/tickets?*", (route) => {
    const pageIndex = Number(new URL(route.request().url()).searchParams.get("page") ?? "0");
    const items = Array.from({ length: pageIndex === 0 ? 25 : 1 }, (_, index) => ({
      ...ticketFixture(
        pageIndex === 0
          ? index === 0
            ? id
            : `55555555-5555-4555-8555-${String(index).padStart(12, "0")}`
          : nextId,
        "another-requester",
      ),
      title: pageIndex === 0 ? `Timed-out ticket ${index}` : "Last timed-out ticket",
    }));
    return route.fulfill({ json: { items, page: pageIndex, size: 25, total: 26 } });
  });
  await page.route("**/tickets/*/intake", (route) =>
    route.fulfill({
      json: {
        contractVersion: "intake-admission@1.0.0",
        ticketId: id,
        contextRevision: 0,
        version: 1,
        reviewRound: 1,
        deadlineAt: "2026-10-27T00:00:00Z",
        lateNotice: false,
        operationalAuthority: false,
        readiness: null,
        finalAssessment: null,
        status: "MANUAL_REQUIRED",
        reviewTaskMayExist: false,
        reasonCodes: ["CLARIFICATION_TIMEOUT"],
      },
    }),
  );
  await page.goto("/servicedesk/intake");
  await expect(page.getByText("Cần điều phối viên xem xét").first()).toBeVisible();
  await expect(page.getByText("Timed-out ticket 0", { exact: true })).toBeVisible();
  await page.getByRole("button", { name: "Trang sau", exact: true }).click();
  await expect(page.getByText("Last timed-out ticket", { exact: true })).toBeVisible();
  await expect(page.getByRole("button", { name: "Trang sau", exact: true })).toBeDisabled();
  await page.getByText("Last timed-out ticket", { exact: true }).click();
  await expect(page).toHaveURL(new RegExp(`/servicedesk/tickets/${nextId}$`));
});
