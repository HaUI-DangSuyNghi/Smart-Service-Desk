import type { Page } from "@playwright/test";

export const homePermissions = [
  "request:create",
  "task:handle",
  "document:view",
  "task:assign",
  "dashboard:view",
];
export const homeDate = "2026-10-31T01:00:00Z";
export const homeRequest = (workspace: string) => ({
  id: `${workspace}-process`,
  processKey: "room-request",
  businessKey: `${workspace}-request`,
  initiator: "home-user",
  startedAt: homeDate,
  endedAt: null,
  activeTaskKeys: ["approve"],
});

export const mockHome = async (page: Page, permissions: string[]) => {
  const calls: string[] = [];
  await page.routeWebSocket(/.*/, () => undefined);
  await page.route("**/src/platform/human/auth/userManager.ts*", (route) =>
    route.fulfill({
      contentType: "application/javascript",
      body: `
      const user = {profile: {sub: "home-user", preferred_username: "Nguyễn Minh Anh"}, expired: false, access_token: "synthetic-token"};
      export const getUserManager = () => ({events: new Proxy({}, {get: () => () => {}}), getUser: async () => user, signinRedirect: async () => {}, signoutRedirect: async () => {}});
      export const restoreSession = async () => user;
      export const currentAccessToken = async () => user.access_token;
      export const renewAccessToken = async () => null;
    `,
    }),
  );
  await page.route(/\/api\/(?:hpdi|core|apps)\//, (route) => {
    const url = new URL(route.request().url());
    const path = url.pathname;
    calls.push(path + url.search);
    const workspace = path.includes("lab-b") ? "lab-b" : "lab-a";
    const paged = (items: unknown[], total = items.length) => ({ items, total, page: 0, size: 20 });
    if (path.endsWith("/me"))
      return route.fulfill({
        json: {
          sub: "home-user",
          username: "Nguyễn Minh Anh",
          platformAdmin: false,
          memberships: [
            { workspace: "lab-a", role: "requester" },
            { workspace: "lab-b", role: "requester" },
          ],
        },
      });
    if (path.endsWith("/workspaces"))
      return route.fulfill({
        json: [
          { workspace: "lab-a", name: "Phòng máy A", roles: ["requester"] },
          { workspace: "lab-b", name: "Phòng máy B", roles: ["requester"] },
        ],
      });
    if (path.endsWith("/permissions"))
      return route.fulfill({ json: permissions.map((key) => ({ key, label: key })) });
    if (path.endsWith("/actor"))
      return route.fulfill({
        json: { subject: "home-user", workspace, roles: ["requester"], capabilities: [] },
      });
    if (path.endsWith("/processes"))
      return route.fulfill({
        json: [
          {
            processKey: "room-request",
            name: "Đặt phòng",
            canStart: true,
            tasks: [{ key: "approve", name: "Duyệt đặt phòng", outcomes: [] }],
            startFields: [{ key: "title", label: "Tiêu đề", multiline: false }],
          },
        ],
      });
    if (path.endsWith("/instances"))
      return route.fulfill({ json: paged([homeRequest(workspace)], 42) });
    if (path.endsWith("/inbox"))
      return route.fulfill({
        json: paged(
          [
            {
              id: "task-1",
              processInstanceId: "process-1",
              processKey: "room-request",
              key: "approve",
              name:
                url.searchParams.get("scope") === "CANDIDATE"
                  ? "Việc có thể nhận"
                  : "Việc được giao",
              businessKey:
                url.searchParams.get("scope") === "CANDIDATE" ? "candidate-room" : "assigned-room",
              candidateGroups: ["lab-a:agent"],
              assignee: "home-user",
              createdAt: homeDate,
            },
          ],
          8,
        ),
      });
    if (path.endsWith("/knowledge"))
      return route.fulfill({
        json: paged(
          [
            {
              id: "guide-1",
              workspace,
              title: "Hướng dẫn sử dụng phòng máy và thiết bị trong học kỳ mới",
              source: "Phòng Công nghệ thông tin",
              version: "1",
              usageRights: "Internal",
              owner: "home-user",
              ownerName: null,
              createdAt: homeDate,
            },
            {
              id: "guide-2",
              workspace,
              title: "Kết nối Wi-Fi trong khuôn viên",
              source: "Phòng Công nghệ thông tin",
              version: "1",
              usageRights: "Internal",
              owner: "home-user",
              ownerName: null,
              createdAt: homeDate,
            },
          ],
          12,
        ),
      });
    if (path.endsWith("/notifications/unread-count")) return route.fulfill({ json: { count: 0 } });
    return route.fulfill({ json: paged([]) });
  });
  return calls;
};
