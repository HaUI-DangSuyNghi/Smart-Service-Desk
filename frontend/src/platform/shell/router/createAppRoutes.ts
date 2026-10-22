import { createElement, lazy, Suspense } from "react";
import type { RouteObject } from "react-router";
import AppLayout from "../layout/AppLayout";
import HomePage from "../layout/HomePage";
import NotFoundPage from "../layout/NotFoundPage";
import type { AppRegistry } from "./registry";

// Landing nặng (ảnh, animation) nên tách bundle riêng
const LandingLayout = lazy(() => import("../landing/LandingLayout"));
const LandingPage = lazy(() => import("../landing/LandingPage"));

/** Ghép route của platform với route các app đã đăng ký. */
export function createAppRoutes(registry: AppRegistry): RouteObject[] {
  const appRoutes = registry
    .apps()
    .flatMap((app) => app.routes)
    .map((route) => ({ path: route.path, element: createElement(route.component) }));

  return [
    // Công khai
    {
      path: "/",
      element: createElement(Suspense, { fallback: null }, createElement(LandingLayout)),
      children: [{ index: true, element: createElement(LandingPage) }],
    },
    // Sau đăng nhập (FE-02 sẽ bọc RequireAuth ở đây)
    {
      element: createElement(AppLayout, { menu: registry.menu() }),
      children: [
        { path: "home", element: createElement(HomePage) },
        ...appRoutes,
        { path: "*", element: createElement(NotFoundPage) },
      ],
    },
  ];
}
