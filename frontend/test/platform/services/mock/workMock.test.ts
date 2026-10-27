import { beforeEach, describe, expect, it, vi } from "vitest";
import { workMock, type NextTaskSpec } from "../../../../src/platform/services/mock/workMock";
import { ApiError } from "../../../../src/platform/shared/api";

// Bỏ độ trễ giả để test chạy nhanh
vi.mock("../../../../src/platform/services/mock/mockMode", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../../../../src/platform/services/mock/mockMode")>()),
  mockDelay: () => Promise.resolve(),
}));

const WORKSPACE = "lab-a";
const USER = { sub: "user-1", name: "Người thử" };

const handle = (overrides: Partial<NextTaskSpec> = {}): NextTaskSpec => ({
  key: "handle",
  name: "Xử lý",
  candidateGroups: [`${WORKSPACE}:agent`],
  assignee: USER.sub,
  queued: true,
  ...overrides,
});

const createWork = (label: string, task: Partial<NextTaskSpec> = {}) =>
  workMock.createProcess({
    workspace: WORKSPACE,
    processKey: "test-flow",
    businessKey: label,
    label,
    initiator: "someone-else",
    task: handle(task),
  });

const statusOf = async (promise: Promise<unknown>) => {
  try {
    await promise;
    return 200;
  } catch (error) {
    return error instanceof ApiError ? error.status : -1;
  }
};

beforeEach(() => {
  workMock.resetWorkMock(USER);
  // Luồng thử: xử lý xong → kết thúc; trả việc → bước duyệt
  workMock.registerFlow("test-flow", {
    next: () => null,
    release: () => ({ key: "review", name: "Duyệt", candidateGroups: [] }),
  });
});

describe("workMock", () => {
  it("Bắt đầu → Tạm dừng → Tiếp tục đổi đúng trạng thái", async () => {
    const process = createWork("A");
    const taskId = process.task!.id;

    expect((await workMock.startWork(WORKSPACE, taskId)).workState).toBe("IN_PROGRESS");
    const paused = await workMock.pauseWork(WORKSPACE, taskId, { reason: "WAITING_PARTS" });
    expect(paused.workState).toBe("PENDING");
    expect(paused.pendingReason).toBe("WAITING_PARTS");
    expect((await workMock.resumeWork(WORKSPACE, taskId)).workState).toBe("IN_PROGRESS");
  });

  it("không cho hai việc Đang làm cùng lúc (409)", async () => {
    const first = createWork("A");
    const second = createWork("B");
    await workMock.startWork(WORKSPACE, first.task!.id);

    expect(await statusOf(workMock.startWork(WORKSPACE, second.task!.id))).toBe(409);
  });

  it("tạm dừng lý do Khác thì bắt buộc ghi chú (400)", async () => {
    const process = createWork("A");
    await workMock.startWork(WORKSPACE, process.task!.id);

    expect(
      await statusOf(workMock.pauseWork(WORKSPACE, process.task!.id, { reason: "OTHER" })),
    ).toBe(400);
  });

  it("người không giữ việc không thao tác được (403)", async () => {
    const process = createWork("A", { assignee: "other-agent" });

    expect(await statusOf(workMock.startWork(WORKSPACE, process.task!.id))).toBe(403);
  });

  it("hàng đợi: đang làm đứng đầu, rồi ưu tiên cao trước", async () => {
    createWork("Thấp", { priority: "LOW" });
    createWork("Khẩn", { priority: "CRITICAL" });
    const running = createWork("Đang làm", { priority: "LOW" });
    await workMock.startWork(WORKSPACE, running.task!.id);

    const queue = await workMock.getQueue(WORKSPACE);
    expect(queue.items.map((item) => item.label)).toEqual(["Đang làm", "Khẩn", "Thấp"]);
    expect(queue.items[0]?.suggestStartNow).toBe(false);
  });

  it("tải: chưa bắt đầu tính đủ ước tính", async () => {
    createWork("A", { estimate: { minutes: 60, basis: "DEFAULT" } });
    createWork("B", { estimate: { minutes: 30, basis: "DEFAULT" } });

    expect((await workMock.getQueue(WORKSPACE)).loadMinutes).toBe(90);
  });

  it("hoàn thành sai bước thì 409, đúng bước thì theo luồng của app", async () => {
    const process = createWork("A");
    const taskId = process.task!.id;

    expect(
      await statusOf(
        workMock.completeTask(WORKSPACE, taskId, { expectedTaskKey: "confirm", outcome: "ok" }),
      ),
    ).toBe(409);
    await workMock.completeTask(WORKSPACE, taskId, { expectedTaskKey: "handle", outcome: "ok" });
    expect(workMock.findProcess(process.processInstanceId)?.endedAt).not.toBeNull();
  });

  it("trả việc thì quay về bước app chọn và ghi lịch sử", async () => {
    const process = createWork("A");

    await workMock.releaseWork(WORKSPACE, process.task!.id, {
      reason: "WRONG_GROUP",
      note: "Không phải lỗi mạng",
    });
    const after = workMock.findProcess(process.processInstanceId);
    expect(after?.task?.key).toBe("review");
    expect(after?.steps.at(-1)).toMatchObject({ outcome: "released", comment: "Không phải lỗi mạng" });
  });

  it("đồng ý yêu cầu sửa ước tính thì đổi ET, nguồn là điều phối viên", async () => {
    const process = createWork("A", { estimate: { minutes: 60, basis: "DEFAULT" } });
    const request = await workMock.requestEstimateChange(WORKSPACE, process.task!.id, {
      proposedMinutes: 120,
      reason: "Phải thay nguồn",
    });

    await workMock.decideEstimateRequest(WORKSPACE, request.id, { approved: true });
    expect(process.task?.estimate).toEqual({ minutes: 120, basis: "COORDINATOR" });
    expect(
      await statusOf(workMock.decideEstimateRequest(WORKSPACE, request.id, { approved: false })),
    ).toBe(409);
  });
});
