import { IconClipboardCheck, IconTicket } from "@tabler/icons-react";
import { lazy } from "react";
import { PERMISSIONS } from "../../platform/human/workspace";
import type { AppRegistry } from "../../platform/shell/router/registry";
import { TicketStatsWidget } from "./dashboard/TicketStatsWidget";
import { ServiceDeskSettingsSection } from "./settings/ServiceDeskSettingsSection";

// Trang lazy hiện skeleton của AppLayout trong lúc tải, không cần giữ thời gian tối thiểu
const TicketListPage = lazy(() => import("./pages/TicketListPage"));
const TicketDetailPage = lazy(() => import("./pages/TicketDetailPage"));
const EnrichmentPendingPage = lazy(() => import("./pages/EnrichmentPendingPage"));
const IntakePendingPage = lazy(() => import("./pages/IntakePendingPage"));
const ReviewQueuePage = lazy(() => import("./pages/ReviewQueuePage"));

export const registerServiceDesk = (registry: AppRegistry) => {
  registry.register({
    id: "servicedesk",
    routes: [
      { path: "servicedesk/tickets", component: TicketListPage },
      // Hai hàng chờ của luồng AI (backend tắt tính năng thì trang báo chưa bật)
      {
        path: "servicedesk/enrichment-pending",
        component: EnrichmentPendingPage,
        requiredPermissions: [PERMISSIONS.taskAssign],
      },
      {
        path: "servicedesk/intake-pending",
        component: IntakePendingPage,
        requiredPermissions: [PERMISSIONS.taskAssign],
      },
      // Không có workspace trong đường dẫn: dùng workspace đang chọn (link từ inbox, danh sách)
      { path: "servicedesk/tickets/:ticketId", component: TicketDetailPage },
      { path: "servicedesk/workspaces/:workspace/tickets/:ticketId", component: TicketDetailPage },
      // Hàng chờ duyệt (R4)
      {
        path: "servicedesk/review",
        component: ReviewQueuePage,
        requiredPermissions: [PERMISSIONS.taskAssign],
      },
    ],
    menu: [
      { label: "Service Desk", path: "/servicedesk/tickets", icon: IconTicket, tone: "orange" },
      {
        label: "Chờ duyệt",
        path: "/servicedesk/review",
        icon: IconClipboardCheck,
        tone: "purple",
        requiredPermissions: [PERMISSIONS.taskAssign],
      },
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
    // Bảng loại sự cố → tổ và ước tính mặc định trên màn Cấu hình hệ thống
    settingsSections: [
      { id: "servicedesk", title: "Service Desk", component: ServiceDeskSettingsSection },
    ],
  });
};
