import { describe, expect, it } from "vitest";
import { createAppRegistry } from "../../../src/platform/shell/router/registry";
import { registerServiceDesk } from "../../../src/apps/servicedesk";

describe("registerServiceDesk", () => {
  it("đăng ký route và menu Service Desk", () => {
    const registry = createAppRegistry();
    registerServiceDesk(registry);

    expect(registry.apps().map((app) => app.id)).toEqual(["servicedesk"]);
    // Một mục duy nhất (không còn nhóm xổ ra), mở thẳng danh sách ticket
    const item = registry.menu().find((entry) => entry.label === "Smart Service Desk");
    expect(item?.path).toBe("/servicedesk/tickets");
    expect(item?.children).toBeUndefined();
  });
});
