import { createBrowserRouter, RouterProvider } from "react-router";
import { registerServiceDesk } from "./apps/servicedesk";
import { createAppRoutes } from "./platform/shell/router/createAppRoutes";
import { createAppRegistry } from "./platform/shell/router/registry";

// Điểm ghép duy nhất giữa platform (Tầng 2) và các app (Tầng 3)
const registry = createAppRegistry();
registerServiceDesk(registry);

const router = createBrowserRouter(createAppRoutes(registry));

export default function App() {
  return <RouterProvider router={router} />;
}
