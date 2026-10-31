import type { Page } from "@playwright/test";

export const ticketFixture = (id: string, creator: string) => ({
  id,
  workspace: "scope-a",
  creator,
  clientRequestId: id,
  title: "Synthetic ticket",
  description: "Synthetic browser contract",
  createdAt: "2026-10-27T00:00:00Z",
  state: "INTAKE",
  process: {
    processInstanceId: "synthetic-process",
    processKey: "service-desk-intake-reviewed",
    businessKey: id,
    workspace: "scope-a",
  },
});

export const mockTicketPage = async (page: Page, id: string, creator: string) => {
  await page.route("**/api/hpdi/v1/workspaces/*/permissions", (route) =>
    route.fulfill({ json: [{ key: "request:create", label: "Create request" }] }),
  );
  await page.route("**/api/apps/servicedesk/v1/workspaces/*/intelligence/status", (route) =>
    route.fulfill({
      json: { mode: "LOCAL_DEV", engine: "rules", modelEnabled: false, assistanceMode: "grounded" },
    }),
  );
  await page.route("**/api/apps/servicedesk/v1/workspaces/*/tickets/**", (route) => {
    const path = new URL(route.request().url()).pathname;
    if (path.endsWith(`/tickets/${id}`)) return route.fulfill({ json: ticketFixture(id, creator) });
    if (path.endsWith("/context-revision"))
      return route.fulfill({
        json: {
          contractVersion: "ticket-context-revision@0.1.0",
          ticketId: id,
          contextRevision: 0,
        },
      });
    return route.fulfill({ status: 404, json: { title: "Not Found" } });
  });
};
