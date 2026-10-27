import { beforeEach, describe, expect, it, vi } from "vitest";
import { ticketMock } from "../../../../src/apps/servicedesk/services/mock/ticketMock";
import { workMock } from "../../../../src/platform/services/mock/workMock";
import { ApiError } from "../../../../src/platform/shared/api";

// Bỏ độ trễ giả để test chạy nhanh
vi.mock("../../../../src/platform/services/mock/mockMode", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../../../../src/platform/services/mock/mockMode")>()),
  mockDelay: () => Promise.resolve(),
}));

const WORKSPACE = "lab-a";
const USER = { sub: "user-1", name: "Người thử" };

const statusOf = async (promise: Promise<unknown>) => {
  try {
    await promise;
    return 200;
  } catch (error) {
    return error instanceof ApiError ? error.status : -1;
  }
};

const newTicket = (clientRequestId = crypto.randomUUID()) =>
  ticketMock.createTicket(WORKSPACE, {
    clientRequestId,
    title: "Máy 15 phòng 1304 không lên nguồn",
    description: "Bấm nút nguồn không lên.",
  });

beforeEach(() => {
  ticketMock.resetTicketMock();
  workMock.resetWorkMock(USER);
});

describe("ticketMock", () => {
  it("ticket mới chờ duyệt, AI chạy nền nên lúc đầu PENDING", async () => {
    const ticket = await newTicket();
    expect(ticket.state).toBe("AWAITING_REVIEW");
    expect(ticket.assessment?.status).toBe("PENDING");
  });

  it("gửi lại cùng clientRequestId thì 409, không tạo trùng (R0)", async () => {
    const id = crypto.randomUUID();
    await newTicket(id);
    expect(await statusOf(newTicket(id))).toBe(409);
  });

  it("duyệt hai lần thì lần sau 409 (R4)", async () => {
    const ticket = await newTicket();
    const decision = {
      category: "HARDWARE" as const,
      priority: "MEDIUM" as const,
      estimateMinutes: 60,
      assignee: "mock-agent-an",
    };
    const reviewed = await ticketMock.reviewTicket(WORKSPACE, ticket.id, decision);
    expect(reviewed.state).toBe("ASSIGNED");
    expect(reviewed.estimate).toEqual({ minutes: 60, basis: "DEFAULT" });
    expect(await statusOf(ticketMock.reviewTicket(WORKSPACE, ticket.id, decision))).toBe(409);
  });

  it("không giao cho chính người tạo ticket (403)", async () => {
    const ticket = await newTicket();
    expect(
      await statusOf(
        ticketMock.reviewTicket(WORKSPACE, ticket.id, {
          category: "HARDWARE",
          priority: "MEDIUM",
          estimateMinutes: 60,
          assignee: USER.sub,
        }),
      ),
    ).toBe(403);
  });

  it("người gửi báo chưa xong thì mở lại cho đúng người đã xử lý (R11)", async () => {
    // Ticket mẫu đang chờ người dùng (USER) xác nhận
    const list = await ticketMock.listTickets(WORKSPACE, {
      page: 0,
      size: 50,
      state: ["AWAITING_CONFIRMATION"],
    });
    const ticket = list.items[0]!;
    const confirm = workMock.findProcess(ticket.process.processInstanceId)!.task!;

    await workMock.completeTask(WORKSPACE, confirm.id, {
      expectedTaskKey: "confirm",
      outcome: "rework",
      comment: "Vẫn không lên màn hình",
    });
    const reopened = await ticketMock.getTicket(WORKSPACE, ticket.id);
    expect(reopened.state).toBe("REOPENED");
    expect(reopened.assignee).toBe("mock-agent-an");
    expect(reopened.reopenCount).toBe(1);
  });

  it("thống kê: các trạng thái cộng lại bằng tổng", async () => {
    const stats = await ticketMock.getStatistics(WORKSPACE);
    const sum =
      (stats.awaitingReview ?? 0) +
      (stats.assigned ?? 0) +
      (stats.inProgress ?? 0) +
      (stats.pending ?? 0) +
      (stats.reopened ?? 0) +
      stats.awaitingConfirmation +
      (stats.closed ?? 0) +
      stats.unclassified;
    expect(sum).toBe(stats.total);
  });
});
