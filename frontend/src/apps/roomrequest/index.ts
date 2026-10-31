import { IconDoor } from "@tabler/icons-react";
import { lazy } from "react";
import type { AppRegistry } from "../../platform/shell/router/registry";

// Trang lazy hiện skeleton của AppLayout trong lúc tải, không cần giữ thời gian tối thiểu
const RoomRequestHomePage = lazy(() => import("./pages/RoomRequestHomePage"));
const RoomRequestDetailPage = lazy(() => import("./pages/RoomRequestDetailPage"));

/**
 * App đặt phòng – miền thứ hai, chứng minh Tầng 2 không phụ thuộc Service Desk:
 * dùng lại Yêu cầu/Công việc/tiến độ (P) và gợi ý AI (I), chỉ thêm form và trang chi tiết.
 */
export const registerRoomRequest = (registry: AppRegistry) => {
  registry.register({
    id: "roomrequest",
    routes: [
      { path: "roomrequest", component: RoomRequestHomePage },
      { path: "roomrequest/requests/:id", component: RoomRequestDetailPage },
    ],
    // Nhóm xổ ra như Service Desk; thêm trang thì thêm mục con
    menu: [
      // Một mục duy nhất, mở thẳng danh sách yêu cầu đặt phòng
      { label: "Đặt phòng", path: "/roomrequest", icon: IconDoor, tone: "turquoise" },
    ],
    // businessKey của quy trình room-request là id yêu cầu
    processLinks: {
      "room-request": (businessKey) => `/roomrequest/requests/${encodeURIComponent(businessKey)}`,
    },
  });
};
