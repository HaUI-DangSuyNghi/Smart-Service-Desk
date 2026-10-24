import { IconTicket } from "@tabler/icons-react";
import { lazy } from "react";
import { withMinDuration } from "../../platform/shared/lib/minDuration";
import type { AppRegistry } from "../../platform/shell/router/registry";

const TicketListPage = lazy(() => withMinDuration(import("./pages/TicketListPage")));
const TicketDetailPage = lazy(() => withMinDuration(import("./pages/TicketDetailPage")));

export const registerServiceDesk = (registry: AppRegistry) => {
  registry.register({
    id: "servicedesk",
    routes: [
      { path: "servicedesk/tickets", component: TicketListPage },
      { path: "servicedesk/workspaces/:workspace/tickets/:ticketId", component: TicketDetailPage },
    ],
    menu: [
      { label: "Service Desk", path: "/servicedesk/tickets", icon: IconTicket, tone: "orange" },
    ],
  });
};
