import { describe, expect, it } from "vitest";
import type { TicketView } from "../../../../../src/apps/servicedesk/services/ticketService";
import {
  dropRejection,
  dropTargets,
} from "../../../../../src/apps/servicedesk/tickets/board/dropTargets";
import type { Viewer } from "../../../../../src/apps/servicedesk/tickets/ticketPermissions";

const ticket = (overrides: Partial<TicketView>): TicketView =>
  ({
    id: "t",
    creator: "requester",
    taskId: "task",
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

const agentA = viewer({});
const agentB = viewer({ subject: "agent-b" });
const coordinator = viewer({ subject: "coord", canHandle: false, canAssign: true });
const requester = viewer({ subject: "requester", canHandle: false });

describe("dropTargets", () => {
  it("điều phối viên, siêu quản trị: Tiếp nhận chỉ thả được vào Chờ duyệt (tiếp nhận)", () => {
    const intake = ticket({ state: "INTAKE", assignee: null, taskId: null });
    expect(dropTargets(intake, coordinator)).toEqual({ review: "intake" });
    expect(dropTargets(intake, viewer({ canHandle: false, isPlatformAdmin: true }))).toEqual({
      review: "intake",
    });
  });

  it("Tiếp nhận: kỹ thuật viên, người gửi, người tạo ticket không kéo được", () => {
    const intake = ticket({ state: "INTAKE", assignee: null });
    expect(dropTargets(intake, agentA)).toEqual({});
    expect(dropTargets(intake, requester)).toEqual({});
    expect(dropTargets(intake, viewer({ subject: "requester", canAssign: true }))).toEqual({});
  });

  it("điều phối viên: Chờ duyệt chỉ thả được vào Đã giao (duyệt và giao việc)", () => {
    const waiting = ticket({ state: "AWAITING_REVIEW", assignee: null });
    expect(dropTargets(waiting, coordinator)).toEqual({ assigned: "review" });
    expect(dropTargets(waiting, viewer({ canHandle: false, isPlatformAdmin: true }))).toEqual({
      assigned: "review",
    });
  });

  it("kỹ thuật viên và người gửi không kéo được ticket chờ duyệt", () => {
    const waiting = ticket({ state: "AWAITING_REVIEW", assignee: null });
    expect(dropTargets(waiting, agentA)).toEqual({});
    expect(dropTargets(waiting, requester)).toEqual({});
  });

  it("người giữ việc: Đã giao và Mở lại → Đang làm (bắt đầu) hoặc Chờ duyệt (trả việc)", () => {
    for (const state of ["ASSIGNED", "REOPENED"] as const) {
      expect(dropTargets(ticket({ state }), agentA)).toEqual({
        inProgress: "start",
        review: "release",
      });
    }
  });

  it("người giữ việc: Đang làm → Đang chờ (tạm dừng), Chờ xác nhận (báo xong), Chờ duyệt (trả việc)", () => {
    expect(dropTargets(ticket({ state: "IN_PROGRESS" }), agentA)).toEqual({
      pending: "pause",
      confirm: "complete",
      review: "release",
    });
  });

  it("người giữ việc: Đang chờ → Đang làm (tiếp tục) hoặc Chờ duyệt (trả việc)", () => {
    expect(dropTargets(ticket({ state: "PENDING" }), agentA)).toEqual({
      inProgress: "resume",
      review: "release",
    });
  });

  it("ticket của người khác thì không kéo được, kể cả điều phối viên đang xử lý", () => {
    expect(dropTargets(ticket({ state: "IN_PROGRESS" }), agentB)).toEqual({});
    expect(dropTargets(ticket({ state: "IN_PROGRESS" }), coordinator)).toEqual({});
  });

  it("điều phối viên (không có quyền xử lý) dù đang giữ ticket cũng không kéo được sang Đang làm", () => {
    const holdingCoordinator = viewer({ subject: "coord", canHandle: false, canAssign: true });
    const assigned = ticket({ state: "ASSIGNED", assignee: "coord" });
    expect(dropTargets(assigned, holdingCoordinator)).toEqual({});
  });

  it("siêu quản trị kéo được ticket mình đang giữ, không kéo được ticket của kỹ thuật viên khác", () => {
    const admin = viewer({ subject: "admin", canAssign: true, isPlatformAdmin: true });
    expect(dropTargets(ticket({ state: "ASSIGNED", assignee: "admin" }), admin)).toEqual({
      inProgress: "start",
      review: "release",
    });
    for (const state of ["ASSIGNED", "REOPENED", "IN_PROGRESS", "PENDING"] as const) {
      expect(dropTargets(ticket({ state, assignee: "agent-b" }), admin)).toEqual({});
    }
  });

  it("Chờ xác nhận, Đã đóng, Không xác định, thiếu việc đang mở: không kéo được", () => {
    for (const state of ["AWAITING_CONFIRMATION", "CLOSED", "UNCLASSIFIED"] as const) {
      expect(dropTargets(ticket({ state }), agentA)).toEqual({});
    }
    expect(dropTargets(ticket({ taskId: null }), agentA)).toEqual({});
  });
});

describe("dropRejection", () => {
  it("nói rõ cột nào thả được", () => {
    expect(dropRejection(ticket({ state: "INTAKE", assignee: null }), coordinator)).toBe(
      'Từ "Tiếp nhận" chỉ chuyển được sang: Chờ duyệt.',
    );
    expect(dropRejection(ticket({ state: "AWAITING_REVIEW", assignee: null }), coordinator)).toBe(
      'Từ "Chờ duyệt" chỉ chuyển được sang: Đã giao.',
    );
    expect(dropRejection(ticket({ state: "IN_PROGRESS" }), agentA)).toBe(
      'Từ "Đang làm" chỉ chuyển được sang: Đang chờ, Chờ xác nhận, Chờ duyệt.',
    );
  });
});
