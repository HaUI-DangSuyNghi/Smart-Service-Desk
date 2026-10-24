import { IconTicket } from "@tabler/icons-react";
import { lazy } from "react";
import type { AppRegistry } from "../../platform/shell/router/registry";

// Trang lazy hiện skeleton của AppLayout trong lúc tải, không cần giữ thời gian tối thiểu
const TicketListPage = lazy(() => import("./pages/TicketListPage"));
const TicketDetailPage = lazy(() => import("./pages/TicketDetailPage"));

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
