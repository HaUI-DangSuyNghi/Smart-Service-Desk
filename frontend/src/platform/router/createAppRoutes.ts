import { createElement } from "react";
import type { RouteObject } from "react-router";
import AppLayout from "../layout/AppLayout";
import HomePage from "../layout/HomePage";
import NotFoundPage from "../layout/NotFoundPage";
import type { AppRegistry } from "./registry";

/** Ghép route của platform với route các app đã đăng ký. */
export function createAppRoutes(registry: AppRegistry): RouteObject[] {
  const appRoutes = registry
    .apps()
    .flatMap((app) => app.routes)
    .map((route) => ({ path: route.path, element: createElement(route.component) }));

  return [
    {
      path: "/",
      element: createElement(AppLayout, { menu: registry.menu() }),
      children: [
        { index: true, element: createElement(HomePage) },
        ...appRoutes,
        { path: "*", element: createElement(NotFoundPage) },
      ],
    },
  ];
}
