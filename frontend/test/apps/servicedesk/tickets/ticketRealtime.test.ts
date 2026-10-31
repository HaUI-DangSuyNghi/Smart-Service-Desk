import { describe, expect, it } from "vitest";
import {
  ticketDetailKeys,
  ticketListKeys,
} from "../../../../src/apps/servicedesk/tickets/useTicketRealtime";

const event = (type: string) => ({ type, workspace: "lab-a", resource: "ticket", id: "t1", at: "" });

describe("ticket realtime keys", () => {
  it("bình luận mới chỉ tải lại bình luận của ticket đó", () => {
    expect(ticketDetailKeys("lab-a", "t1", event("TICKET_COMMENTED"))).toEqual([
      ["servicedesk", "tickets", "lab-a", "comments", "t1"],
    ]);
  });

  it("ticket đổi thì tải lại chi tiết, tiếp nhận và tiến độ", () => {
    const keys = ticketDetailKeys("lab-a", "t1", event("TICKET_CHANGED"));
    expect(keys).toContainEqual(["servicedesk", "tickets", "lab-a", "detail", "t1"]);
    expect(keys).toContainEqual(["servicedesk", "intake", "lab-a", "t1"]);
    expect(keys).toContainEqual(["process", "lab-a"]);
    expect(keys).not.toContainEqual(["servicedesk", "tickets", "lab-a", "comments", "t1"]);
  });

  it("danh sách tải lại cả nhóm ticket và thống kê của workspace", () => {
    expect(ticketListKeys("lab-a")).toEqual([
      ["servicedesk", "tickets", "lab-a"],
      ["servicedesk", "statistics", "lab-a"],
    ]);
  });
});
