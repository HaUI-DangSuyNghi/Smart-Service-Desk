import { QueryClientProvider } from "@tanstack/react-query";
import { createBrowserRouter, RouterProvider } from "react-router";
import { registerServiceDesk } from "./apps/servicedesk";
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
