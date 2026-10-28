import { IconClipboardCheck, IconTicket } from "@tabler/icons-react";
import { lazy } from "react";
import { PERMISSIONS } from "../../platform/human/workspace";
import { isWorkflowAvailable } from "../../platform/services";
import type { AppRegistry, AppRoute, MenuItem } from "../../platform/shell/router/registry";
import { TicketStatsWidget } from "./dashboard/TicketStatsWidget";
import { ServiceDeskSettingsSection } from "./settings/ServiceDeskSettingsSection";

// Trang lazy hiện skeleton của AppLayout trong lúc tải, không cần giữ thời gian tối thiểu
const TicketListPage = lazy(() => import("./pages/TicketListPage"));
const TicketDetailPage = lazy(() => import("./pages/TicketDetailPage"));
const EnrichmentPendingPage = lazy(() => import("./pages/EnrichmentPendingPage"));
const IntakePendingPage = lazy(() => import("./pages/IntakePendingPage"));
const ReviewQueuePage = lazy(() => import("./pages/ReviewQueuePage"));

// Hàng chờ duyệt (R4) chỉ có khi backend sẵn sàng hoặc bật dữ liệu giả
const REVIEW_ROUTES: readonly AppRoute[] = isWorkflowAvailable
  ? [
      {
        path: "servicedesk/review",
        component: ReviewQueuePage,
        requiredPermissions: [PERMISSIONS.taskAssign],
      },
    ]
  : [];

const REVIEW_MENU: readonly MenuItem[] = isWorkflowAvailable
  ? [
      {
        label: "Chờ duyệt",
        path: "/servicedesk/review",
        icon: IconClipboardCheck,
        tone: "purple",
        requiredPermissions: [PERMISSIONS.taskAssign],
      },
    ]
  : [];

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
      ...REVIEW_ROUTES,
    ],
    menu: [
      { label: "Service Desk", path: "/servicedesk/tickets", icon: IconTicket, tone: "orange" },
      ...REVIEW_MENU,
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
    settingsSections: isWorkflowAvailable
      ? [{ id: "servicedesk", title: "Service Desk", component: ServiceDeskSettingsSection }]
      : [],
  });
};
