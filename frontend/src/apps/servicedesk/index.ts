import { IconClipboardList, IconTicket } from "@tabler/icons-react";
import { lazy } from "react";
import { PERMISSIONS } from "../../platform/human/workspace";
import type { AppRegistry } from "../../platform/shell/router/registry";
import { TicketStatsWidget } from "./dashboard/TicketStatsWidget";
import { ServiceDeskSettingsSection } from "./settings/ServiceDeskSettingsSection";

// Trang lazy hiện skeleton của AppLayout trong lúc tải, không cần giữ thời gian tối thiểu
const TicketListPage = lazy(() => import("./pages/TicketListPage"));
const TicketDetailPage = lazy(() => import("./pages/TicketDetailPage"));
const IntakeQueuePage = lazy(() => import("./pages/IntakeQueuePage"));

export const registerServiceDesk = (registry: AppRegistry) => {
  registry.register({
    id: "servicedesk",
    routes: [
      { path: "servicedesk/tickets", component: TicketListPage },
      // Không có workspace trong đường dẫn: dùng workspace đang chọn (link từ inbox, danh sách)
      { path: "servicedesk/tickets/:ticketId", component: TicketDetailPage },
      { path: "servicedesk/workspaces/:workspace/tickets/:ticketId", component: TicketDetailPage },
      // Ticket đang tiếp nhận; chờ duyệt là view trong trang Ticket
      {
        path: "servicedesk/intake",
        component: IntakeQueuePage,
        requiredPermissions: [PERMISSIONS.taskAssign],
      },
    ],
    // Nhóm xổ ra trên sidebar
    menu: [
      {
        label: "Service Desk",
        path: "/servicedesk",
        icon: IconTicket,
        tone: "orange",
        // App chính: mở sẵn để thấy ngay các trang ticket
        defaultOpen: true,
        children: [
          { label: "Ticket", path: "/servicedesk/tickets", icon: IconTicket },
          {
            label: "Tiếp nhận",
            path: "/servicedesk/intake",
            icon: IconClipboardList,
            requiredPermissions: [PERMISSIONS.taskAssign],
          },
        ],
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
