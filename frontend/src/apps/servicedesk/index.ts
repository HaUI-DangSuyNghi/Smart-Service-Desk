import { IconTicket } from "@tabler/icons-react";
import { lazy } from "react";
import type { AppRegistry } from "../../platform/shell/router/registry";
import { TicketStatsWidget } from "./dashboard/TicketStatsWidget";

// Trang lazy hiện skeleton của AppLayout trong lúc tải, không cần giữ thời gian tối thiểu
const TicketListPage = lazy(() => import("./pages/TicketListPage"));
const TicketDetailPage = lazy(() => import("./pages/TicketDetailPage"));
const EnrichmentPendingPage = lazy(() => import("./pages/EnrichmentPendingPage"));
const IntakePendingPage = lazy(() => import("./pages/IntakePendingPage"));

export const registerServiceDesk = (registry: AppRegistry) => {
  registry.register({
    id: "servicedesk",
    routes: [
      { path: "servicedesk/tickets", component: TicketListPage },
      { path: "servicedesk/enrichment-pending", component: EnrichmentPendingPage },
      { path: "servicedesk/intake-pending", component: IntakePendingPage },
      // Không có workspace trong đường dẫn: dùng workspace đang chọn (link từ inbox, danh sách)
      { path: "servicedesk/tickets/:ticketId", component: TicketDetailPage },
      { path: "servicedesk/workspaces/:workspace/tickets/:ticketId", component: TicketDetailPage },
    ],
    menu: [
      { label: "Service Desk", path: "/servicedesk/tickets", icon: IconTicket, tone: "orange" },
    ],
    // businessKey của quy trình service-desk là id ticket
    processLinks: {
      "service-desk": (businessKey) => `/servicedesk/tickets/${encodeURIComponent(businessKey)}`,
      "service-desk-reviewed": (businessKey) =>
        `/servicedesk/tickets/${encodeURIComponent(businessKey)}`,
      "service-desk-intake-reviewed": (businessKey) =>
        `/servicedesk/tickets/${encodeURIComponent(businessKey)}`,
    },
    // Thống kê ticket hiện trên dashboard chung của điều phối viên
    dashboardWidgets: [
      { id: "servicedesk-tickets", title: "Service Desk – ticket", component: TicketStatsWidget },
    ],
  });
};
