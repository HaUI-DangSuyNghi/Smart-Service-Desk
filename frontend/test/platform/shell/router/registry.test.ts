import { describe, expect, it } from "vitest";
import { createAppRoutes } from "../../../../src/platform/shell/router/createAppRoutes";
import { createAppRegistry } from "../../../../src/platform/shell/router/registry";

const Page = () => null;

describe("createAppRegistry", () => {
  it("gom route, menu và link process của các app", () => {
    const registry = createAppRegistry();
    registry.register({
      id: "demo",
      routes: [{ path: "demo", component: Page }],
      menu: [{ label: "Demo", path: "/demo" }],
      processLinks: { "demo-process": (key) => `/demo/${key}` },
    });

    expect(registry.apps()).toHaveLength(1);
    expect(registry.menu()).toEqual([{ label: "Demo", path: "/demo" }]);
    expect(registry.processLink("demo-process", "42")).toBe("/demo/42");
    expect(registry.processLink("unknown", "42")).toBeNull();
  });

  it("chặn id, route và processKey bị trùng", () => {
    const registry = createAppRegistry();
    registry.register({
      id: "a",
      routes: [{ path: "a", component: Page }],
      processLinks: { p: () => "/a" },
    });

    expect(() => registry.register({ id: "a", routes: [] })).toThrow();
    expect(() =>
      registry.register({ id: "b", routes: [{ path: "a", component: Page }] }),
    ).toThrow();
    expect(() =>
      registry.register({ id: "c", routes: [], processLinks: { p: () => "/c" } }),
    ).toThrow();
  });
});

describe("createAppRoutes", () => {
  it("landing và callback công khai; trang platform, route của app và 404 nằm sau RequireAuth", () => {
    const registry = createAppRegistry();
    registry.register({ id: "demo", routes: [{ path: "demo", component: Page }] });

    const [landing, callback, protectedArea] = createAppRoutes(registry);
    expect(landing?.path).toBe("/");
    expect(callback?.path).toBe("/auth/callback");

    const layout = protectedArea?.children?.[0];
    expect(layout?.children?.map((route) => route.path)).toEqual([
      "home",
      "dashboard",
      "audit",
      "profile",
      "inbox",
      "requests",
      "processes/:id",
      "knowledge",
      "knowledge/:id",
      "alerts",
      "notifications",
      "duty",
      "estimate-requests",
      "demo",
      "settings",
      "*",
    ]);
  });
});
