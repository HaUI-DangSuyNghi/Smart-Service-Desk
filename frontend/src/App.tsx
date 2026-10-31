import { QueryClientProvider } from "@tanstack/react-query";
import { createBrowserRouter, RouterProvider } from "react-router";
import { registerRoomRequest } from "./apps/roomrequest";
import { registerServiceDesk } from "./apps/servicedesk";
import { configureDashboardWidgets } from "./platform/data/dashboard";
import { AuthProvider, httpAuth } from "./platform/human/auth";
import { configureProcessLinks, configureRequestActions } from "./platform/process";
import { configureHttpAuth, queryClient } from "./platform/shared/api";
import { createAppRoutes } from "./platform/shell/router/createAppRoutes";
import { createAppRegistry } from "./platform/shell/router/registry";

// Điểm ghép duy nhất giữa platform (Tầng 2) và các app (Tầng 3)
const registry = createAppRegistry();
registerServiceDesk(registry);
registerRoomRequest(registry);

const router = createBrowserRouter(createAppRoutes(registry));

configureHttpAuth(httpAuth);
// Công việc, tiến độ (Tầng 2) dẫn sang trang chi tiết mà app đăng ký theo processKey
configureProcessLinks(registry.processLink);
// Trang Yêu cầu chung hiện thêm nút thao tác do app đăng ký (ví dụ người gửi xác nhận kết quả)
configureRequestActions(registry.requestAction);
// Dashboard chung (Tầng 2) hiện thêm khối số liệu do các app đăng ký
configureDashboardWidgets(registry.dashboardWidgets());

const App = () => {
  return (
    <QueryClientProvider client={queryClient}>
      <AuthProvider>
        <RouterProvider router={router} />
      </AuthProvider>
    </QueryClientProvider>
  );
};

export default App;
