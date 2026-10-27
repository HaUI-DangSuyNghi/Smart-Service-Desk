import { describe, expect, it } from "vitest";
import type { TicketView } from "../../../../../src/apps/servicedesk/services/ticketService";
import {
  boardAccess,
  boardActions,
} from "../../../../../src/apps/servicedesk/tickets/board/boardAccess";
import type { Viewer } from "../../../../../src/apps/servicedesk/tickets/ticketPermissions";

const base: Viewer = { subject: "u", canHandle: false, canAssign: false, isPlatformAdmin: false };
const requester = base;
const agent: Viewer = { ...base, canHandle: true };
const coordinator: Viewer = { ...base, canAssign: true };
const admin: Viewer = { ...base, isPlatformAdmin: true };

const ticket = (state: TicketView["state"]) => ({ id: "t", taskId: "task", state }) as TicketView;

describe("boardAccess", () => {
  it("người gửi không có Board", () => {
    expect(boardAccess(requester)).toEqual({ canView: false, canOpen: false, canEdit: false });
  });

  it("kỹ thuật viên xem và mở thẻ nhưng không sửa", () => {
    expect(boardAccess(agent)).toEqual({ canView: true, canOpen: true, canEdit: false });
  });

  it("điều phối viên và siêu quản trị sửa được", () => {
    expect(boardAccess(coordinator).canEdit).toBe(true);
    expect(boardAccess(admin).canEdit).toBe(true);
  });
});

describe("boardActions", () => {
  it("kỹ thuật viên không có mục sửa nào", () => {
    expect(boardActions(ticket("IN_PROGRESS"), agent)).toEqual([]);
    expect(boardActions(ticket("AWAITING_REVIEW"), agent)).toEqual([]);
  });

  it("chờ duyệt: chỉ Duyệt và giao việc; không có Nhận việc ở mọi vai", () => {
    expect(boardActions(ticket("AWAITING_REVIEW"), coordinator)).toEqual(["review"]);
    expect(boardActions(ticket("AWAITING_REVIEW"), admin)).toEqual(["review"]);
  });

  it("đang xử lý: giao lại và sửa phân loại", () => {
    for (const state of ["ASSIGNED", "IN_PROGRESS", "PENDING", "REOPENED"] as const) {
      expect(boardActions(ticket(state), coordinator)).toEqual(["reassign", "correct"]);
    }
  });

  it("chờ xác nhận, đã đóng, thiếu việc đang mở: không có mục nào", () => {
    expect(boardActions(ticket("AWAITING_CONFIRMATION"), coordinator)).toEqual([]);
    expect(boardActions(ticket("CLOSED"), admin)).toEqual([]);
    expect(boardActions({ ...ticket("IN_PROGRESS"), taskId: null } as TicketView, admin)).toEqual(
      [],
    );
  });
});
