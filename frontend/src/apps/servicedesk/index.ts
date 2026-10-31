import { IconTicket } from "@tabler/icons-react";
import { lazy } from "react";
import { PERMISSIONS } from "../../platform/human/workspace";
import type { AppRegistry } from "../../platform/shell/router/registry";
import { TicketStatsWidget } from "./dashboard/TicketStatsWidget";
import { ServiceDeskSettingsSection } from "./settings/ServiceDeskSettingsSection";
import { TicketRequestAction } from "./tickets/TicketRequestAction";
import { TicketInboxAction } from "./tickets/TicketInboxAction";

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
    // Mục trên sidebar
    menu: [
      // Một mục duy nhất, mở thẳng danh sách ticket (trang Tiếp nhận vẫn mở được qua đường dẫn)
      {
        label: "Smart Service Desk",
        path: "/servicedesk/tickets",
        icon: IconTicket,
        tone: "orange",
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
    // Trang Yêu cầu chung: người gửi Xác nhận (Chờ xác nhận) hoặc Bổ sung thông tin ngay trên dòng
    requestActions: {
      "service-desk": TicketRequestAction,
      "service-desk-reviewed": TicketRequestAction,
      "service-desk-intake-reviewed": TicketRequestAction,
    },
    inboxActions: {
      "service-desk": TicketInboxAction,
      "service-desk-reviewed": TicketInboxAction,
      "service-desk-intake-reviewed": TicketInboxAction,
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
