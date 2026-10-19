import { lazy } from "react";
import type { AppRegistry } from "../../platform/router/registry";

const TicketListPage = lazy(() => import("./pages/TicketListPage"));

export function registerServiceDesk(registry: AppRegistry) {
  registry.register({
    id: "servicedesk",
    routes: [{ path: "servicedesk/tickets", component: TicketListPage }],
    menu: [{ label: "Service Desk", path: "/servicedesk/tickets" }],
  });
}
