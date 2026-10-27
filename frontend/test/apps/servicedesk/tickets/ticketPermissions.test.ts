import { describe, expect, it } from "vitest";
import {
  ticketPermissions,
  type Viewer,
} from "../../../../src/apps/servicedesk/tickets/ticketPermissions";
import type { TicketView } from "../../../../src/apps/servicedesk/services/ticketService";

const ticket = (overrides: Partial<TicketView>): TicketView =>
  ({
    id: "t1",
    workspace: "lab",
    creator: "requester",
    title: "x",
    description: "y",
    createdAt: new Date().toISOString(),
    taskId: "task-1",
    state: "IN_PROGRESS",
    assignee: "agent-a",
    ...overrides,
  }) as TicketView;

const viewer = (overrides: Partial<Viewer>): Viewer => ({
  subject: "agent-a",
  canHandle: true,
  canAssign: false,
  isPlatformAdmin: false,
  ...overrides,
});

describe("ticketPermissions", () => {
  it("kỹ thuật viên giữ việc: thao tác và sửa phân loại ticket của mình", () => {
    const result = ticketPermissions(ticket({}), viewer({}));
    expect(result.isHolder).toBe(true);
    expect(result.canAct).toBe(true);
    expect(result.canCorrect).toBe(true);
    expect(result.readOnly).toBe(false);
  });

  it("kỹ thuật viên A xem ticket của B: chỉ xem", () => {
    const result = ticketPermissions(ticket({ assignee: "agent-b" }), viewer({}));
    expect(result.canAct).toBe(false);
    expect(result.canCorrect).toBe(false);
    expect(result.canReview).toBe(false);
    expect(result.readOnly).toBe(true);
  });

  it("điều phối viên duyệt ticket chờ duyệt và sửa phân loại ticket của người khác", () => {
    const coordinator = viewer({ subject: "coord", canHandle: false, canAssign: true });
    const review = ticketPermissions(
      ticket({ state: "AWAITING_REVIEW", assignee: null }),
      coordinator,
    );
    expect(review.canReview).toBe(true);
    expect(review.canAct).toBe(false);
    const handling = ticketPermissions(ticket({ assignee: "agent-b" }), coordinator);
    expect(handling.canCorrect).toBe(true);
    expect(handling.canAct).toBe(false);
  });

  it("người gửi chỉ xác nhận ở bước chờ xác nhận của ticket mình", () => {
    const requester = viewer({ subject: "requester", canHandle: false });
    const confirm = ticketPermissions(
      ticket({ state: "AWAITING_CONFIRMATION", assignee: "requester" }),
      requester,
    );
    expect(confirm.canConfirm).toBe(true);
    const other = ticketPermissions(
      ticket({ state: "AWAITING_CONFIRMATION", creator: "someone" }),
      requester,
    );
    expect(other.canConfirm).toBe(false);
    expect(other.readOnly).toBe(true);
  });

  it("ticket đã đóng: không ai thao tác được", () => {
    const result = ticketPermissions(
      ticket({ state: "CLOSED", taskId: null }),
      viewer({ canAssign: true }),
    );
    expect(result.readOnly).toBe(true);
  });

  it("chưa biết người xem (đang tải) thì không có quyền nào", () => {
    const result = ticketPermissions(ticket({}), viewer({ subject: null, canHandle: false }));
    expect(result.isHolder).toBe(false);
    expect(result.readOnly).toBe(true);
  });
});
