import { describe, expect, it } from "vitest";
import { createAppRegistry } from "../../../src/platform/shell/router/registry";
import { registerRoomRequest } from "../../../src/apps/roomrequest";
import { registerServiceDesk } from "../../../src/apps/servicedesk";

describe("registerRoomRequest", () => {
  it("đăng ký route, menu và link sang trang chi tiết theo processKey", () => {
    const registry = createAppRegistry();
    registerRoomRequest(registry);

    const group = registry.menu().find((item) => item.label === "Đặt phòng");
    expect(group?.children).toContainEqual(expect.objectContaining({ path: "/roomrequest" }));
    expect(registry.processLink("room-request", "abc")).toBe("/roomrequest/requests/abc");
  });

  it("chạy cùng Service Desk, mỗi app giữ link của quy trình mình", () => {
    const registry = createAppRegistry();
    registerServiceDesk(registry);
    registerRoomRequest(registry);

    expect(registry.apps().map((app) => app.id)).toEqual(["servicedesk", "roomrequest"]);
    expect(registry.processLink("service-desk", "t1")).toBe("/servicedesk/tickets/t1");
    expect(registry.processLink("equipment-approval", "x")).toBeNull();
  });
});
