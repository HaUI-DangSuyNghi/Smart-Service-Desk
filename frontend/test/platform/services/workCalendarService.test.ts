import { beforeEach, describe, expect, it, vi } from "vitest";
import { getCalendarWork } from "../../../src/platform/services/workCalendarService";
import { getQueue, type QueueItem } from "../../../src/platform/services/workService";

vi.mock("../../../src/platform/services/workService", () => ({ getQueue: vi.fn() }));
const queue = vi.mocked(getQueue);
const task = (id: string) => ({ id }) as QueueItem;

beforeEach(() => queue.mockReset());

describe("tải toàn bộ việc cho lịch", () => {
  it("tải cả trang tiếp theo và truyền tín hiệu huỷ cho từng yêu cầu", async () => {
    queue.mockResolvedValueOnce({
      items: [task("a"), task("b")],
      page: 0,
      size: 2,
      total: 3,
      assignee: "me",
      loadMinutes: 90,
    });
    queue.mockResolvedValueOnce({
      items: [task("c")],
      page: 1,
      size: 2,
      total: 3,
      assignee: "me",
      loadMinutes: 90,
    });
    const signal = new AbortController().signal;
    expect((await getCalendarWork("scope-a", signal)).map((item) => item.id)).toEqual([
      "a",
      "b",
      "c",
    ]);
    expect(queue).toHaveBeenNthCalledWith(2, "scope-a", { page: 1, size: 2 }, signal);
  });

  it("trang sau bị lỗi thì không trả về lịch thiếu dữ liệu", async () => {
    queue.mockResolvedValueOnce({
      items: [task("a")],
      page: 0,
      size: 1,
      total: 2,
      assignee: "me",
      loadMinutes: 20,
    });
    queue.mockRejectedValueOnce(new Error("Failed page"));
    await expect(getCalendarWork("scope-a")).rejects.toThrow("Failed page");
  });

  it("hàng đợi rỗng không tải thêm trang", async () => {
    queue.mockResolvedValue({
      items: [],
      page: 0,
      size: 50,
      total: 0,
      assignee: "me",
      loadMinutes: 0,
    });
    expect(await getCalendarWork("scope-a")).toEqual([]);
    expect(queue).toHaveBeenCalledTimes(1);
  });
});
