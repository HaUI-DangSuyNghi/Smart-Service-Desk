import { describe, expect, it } from "vitest";
import { createAppRegistry } from "../../../src/platform/shell/router/registry";
import { registerServiceDesk } from "../../../src/apps/servicedesk";

describe("registerServiceDesk", () => {
  it("đăng ký route và menu Service Desk", () => {
    const registry = createAppRegistry();
    registerServiceDesk(registry);

    expect(registry.apps().map((app) => app.id)).toEqual(["servicedesk"]);
    const group = registry.menu().find((item) => item.label === "Service Desk");
    expect(group?.children?.map((child) => child.path)).toEqual([
      "/servicedesk/tickets",
      "/servicedesk/intake",
    ]);
  });
});
