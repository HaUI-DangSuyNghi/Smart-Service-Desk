import { QueryClientProvider } from "@tanstack/react-query";
import { createBrowserRouter, RouterProvider } from "react-router";
import { registerServiceDesk } from "./apps/servicedesk";
import { configureDashboardWidgets } from "./platform/data/dashboard";
import { AuthProvider, httpAuth } from "./platform/human/auth";
import { configureProcessLinks } from "./platform/process";
import { configureHttpAuth, queryClient } from "./platform/shared/api";
import { createAppRoutes } from "./platform/shell/router/createAppRoutes";
import { createAppRegistry } from "./platform/shell/router/registry";

// Điểm ghép duy nhất giữa platform (Tầng 2) và các app (Tầng 3)
const registry = createAppRegistry();
registerServiceDesk(registry);

const router = createBrowserRouter(createAppRoutes(registry));

configureHttpAuth(httpAuth);
// Công việc, tiến độ (Tầng 2) dẫn sang trang chi tiết mà app đăng ký theo processKey
configureProcessLinks(registry.processLink);
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
