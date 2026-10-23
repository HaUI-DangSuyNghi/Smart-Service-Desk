import { createElement } from "react";
import type { RouteObject } from "react-router";
import { CALLBACK_PATH, CallbackPage, RequireAuth } from "@/platform/human/auth";
// Landing nạp cùng app (không lazy): là trang đầu tiên và trang quay về sau đăng xuất,
// tránh thêm một lượt loading nữa sau khi Keycloak chuyển về
import LandingLayout from "../landing/LandingLayout";
import AppLayout from "../layout/AppLayout";
import HomePage from "../layout/HomePage";
import NotFoundPage from "../layout/NotFoundPage";
import type { AppRegistry } from "./registry";

/** Ghép route của platform với route các app đã đăng ký. */
export const createAppRoutes = (registry: AppRegistry): RouteObject[] => {
  const appRoutes = registry
    .apps()
    .flatMap((app) => app.routes)
    .map((route) => ({ path: route.path, element: createElement(route.component) }));

  return [
    // Công khai
    { path: "/", element: createElement(LandingLayout) },
    { path: CALLBACK_PATH, element: createElement(CallbackPage) },

    // Cần đăng nhập
    {
      element: createElement(RequireAuth),
      children: [
        {
          element: createElement(AppLayout, { menu: registry.menu() }),
          children: [
            { path: "home", element: createElement(HomePage) },
            ...appRoutes,
            { path: "*", element: createElement(NotFoundPage) },
          ],
        },
      ],
    },
  ];
};
