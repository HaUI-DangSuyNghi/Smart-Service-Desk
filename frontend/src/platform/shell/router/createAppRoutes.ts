import { createElement } from "react";
import type { RouteObject } from "react-router";
import { KnowledgeDetailPage, KnowledgeListPage } from "@/platform/data/knowledge";
import { CALLBACK_PATH, CallbackPage, RequireAuth } from "@/platform/human/auth";
import MembersPage from "@/platform/human/members/MembersPage";
import ProfilePage from "@/platform/human/profile/ProfilePage";
import { RoleGate } from "@/platform/human/workspace";
// Landing nạp cùng app (không lazy): là trang đầu tiên và trang quay về sau đăng xuất,
// tránh thêm một lượt loading nữa sau khi Keycloak chuyển về
import LandingLayout from "../landing/LandingLayout";
import AppLayout from "../layout/AppLayout";
import ForbiddenPage from "../layout/ForbiddenPage";
import HomePage from "../layout/HomePage";
import NotFoundPage from "../layout/NotFoundPage";
import type { AppRegistry, AppRoute } from "./registry";

/** Trang có yêu cầu vai trò thì bọc RoleGate, thiếu quyền hiện trang 403. */
const routeElement = (route: AppRoute) => {
  const page = createElement(route.component);
  if (!route.requiredRoles?.length) return page;
  return createElement(RoleGate, {
    roles: route.requiredRoles,
    fallback: createElement(ForbiddenPage),
    children: page,
  });
};

/** Trang của Tầng 2 (platform), đăng ký giống trang của app. */
const PLATFORM_ROUTES: readonly AppRoute[] = [
  { path: "home", component: HomePage },
  { path: "profile", component: ProfilePage },
  { path: "members", component: MembersPage },
  { path: "knowledge", component: KnowledgeListPage },
  { path: "knowledge/:id", component: KnowledgeDetailPage },
];

/** Ghép route của platform với route các app đã đăng ký. */
export const createAppRoutes = (registry: AppRegistry): RouteObject[] => {
  const pages = [...PLATFORM_ROUTES, ...registry.apps().flatMap((app) => app.routes)].map(
    (route) => ({
      path: route.path,
      element: routeElement(route),
    }),
  );

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
          children: [...pages, { path: "*", element: createElement(NotFoundPage) }],
        },
      ],
    },
  ];
};
