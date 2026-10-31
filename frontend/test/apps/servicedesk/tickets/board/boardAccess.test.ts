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

  it("đang tiếp nhận: chỉ Tiếp nhận, cho điều phối viên và siêu quản trị", () => {
    const intake = { ...ticket("INTAKE"), taskId: null } as TicketView;
    expect(boardActions(intake, coordinator)).toEqual(["intake"]);
    expect(boardActions(intake, admin)).toEqual(["intake"]);
    expect(boardActions(intake, agent)).toEqual([]);
    expect(boardActions({ ...intake, creator: "u" } as TicketView, coordinator)).toEqual([]);
  });

  it("chờ duyệt: chỉ Duyệt và giao việc; không có Nhận việc ở mọi vai", () => {
    expect(boardActions(ticket("AWAITING_REVIEW"), coordinator)).toEqual(["review"]);
    expect(boardActions(ticket("AWAITING_REVIEW"), admin)).toEqual(["review"]);
  });

  it("đang xử lý: giao lại và sửa phân loại", () => {
    for (const state of ["ASSIGNED", "IN_PROGRESS", "PENDING"] as const) {
      expect(boardActions(ticket(state), coordinator)).toEqual(["reassign", "correct"]);
    }
    const reassigned = { ...ticket("REOPENED"), workState: "NOT_STARTED" } as TicketView;
    expect(boardActions(reassigned, coordinator)).toEqual(["reassign", "correct"]);
  });

  it("mở lại chưa giao lại: quay về bước duyệt", () => {
    expect(boardActions(ticket("REOPENED"), coordinator)).toEqual(["review"]);
  });

  it("ticket do chính mình tạo: không tự duyệt, không tự sửa phân loại", () => {
    const own = (state: TicketView["state"]) => ({ ...ticket(state), creator: "u" }) as TicketView;
    expect(boardActions(own("AWAITING_REVIEW"), coordinator)).toEqual(["edit", "cancel"]);
    expect(boardActions(own("IN_PROGRESS"), coordinator)).toEqual(["close", "reassign"]);
  });

  it("chỉ chủ ticket có sửa/hủy trước duyệt và đóng sau duyệt ở mọi vai trò", () => {
    for (const viewer of [requester, agent, coordinator, admin]) {
      const own = (state: TicketView["state"]) =>
        ({ ...ticket(state), creator: "u" }) as TicketView;
      expect(boardActions(own("INTAKE"), viewer)).toEqual(["edit", "cancel"]);
      expect(boardActions(own("AWAITING_REVIEW"), viewer)).toEqual(["edit", "cancel"]);
      expect(boardActions(own("AWAITING_CONFIRMATION"), viewer)).toEqual(["close"]);
      expect(boardActions(own("CLOSED"), viewer)).toEqual([]);
      expect(boardActions(own("CANCELLED"), viewer)).toEqual([]);
      expect(boardActions(ticket("AWAITING_REVIEW"), viewer)).not.toContain("edit");
      expect(boardActions(ticket("AWAITING_CONFIRMATION"), viewer)).not.toContain("close");
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
