import { createElement } from "react";
import type { RouteObject } from "react-router";
import { AuditPage } from "@/platform/data/audit";
import { DashboardPage } from "@/platform/data/dashboard";
import { KnowledgeDetailPage, KnowledgeListPage } from "@/platform/data/knowledge";
import { CALLBACK_PATH, CallbackPage, RequireAuth } from "@/platform/human/auth";
import MembersPage from "@/platform/human/members/MembersPage";
import ProfilePage from "@/platform/human/profile/ProfilePage";
import { PERMISSIONS, PermissionGate, RoleGate } from "@/platform/human/workspace";
import { InboxPage, MyRequestsPage, ProcessTimelinePage } from "@/platform/process";
// Landing nạp cùng app (không lazy): là trang đầu tiên và trang quay về sau đăng xuất,
// tránh thêm một lượt loading nữa sau khi Keycloak chuyển về
import LandingLayout from "../landing/LandingLayout";
import AppLayout from "../layout/AppLayout";
import ForbiddenPage from "../layout/ForbiddenPage";
import HomePage from "../layout/HomePage";
import NotFoundPage from "../layout/NotFoundPage";
import type { AppRegistry, AppRoute } from "./registry";

/** Trang có yêu cầu quyền hoặc vai trò thì bọc cổng kiểm tra; thiếu thì hiện trang 403. */
const routeElement = (route: AppRoute) => {
  let element = createElement(route.component);
  if (route.requiredPermissions?.length) {
    element = createElement(PermissionGate, {
      permissions: route.requiredPermissions,
      fallback: createElement(ForbiddenPage),
      children: element,
    });
  }
  if (route.requiredRoles?.length) {
    element = createElement(RoleGate, {
      roles: route.requiredRoles,
      fallback: createElement(ForbiddenPage),
      children: element,
    });
  }
  return element;
};

/** Trang của Tầng 2 (platform), đăng ký giống trang của app. */
const PLATFORM_ROUTES: readonly AppRoute[] = [
  { path: "home", component: HomePage },
  { path: "dashboard", component: DashboardPage, requiredPermissions: [PERMISSIONS.dashboardView] },
  { path: "audit", component: AuditPage, requiredPermissions: [PERMISSIONS.auditView] },
  { path: "profile", component: ProfilePage },
  { path: "members", component: MembersPage, requiredPermissions: [PERMISSIONS.memberView] },
  { path: "inbox", component: InboxPage, requiredPermissions: [PERMISSIONS.taskHandle] },
  { path: "requests", component: MyRequestsPage, requiredPermissions: [PERMISSIONS.requestCreate] },
  // Ai xem được tiến độ do backend quyết định theo từng quy trình (người tạo, người đã xử lý...)
  { path: "processes/:id", component: ProcessTimelinePage },
  {
    path: "knowledge",
    component: KnowledgeListPage,
    requiredPermissions: [PERMISSIONS.documentView],
  },
  {
    path: "knowledge/:id",
    component: KnowledgeDetailPage,
    requiredPermissions: [PERMISSIONS.documentView],
  },
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
