import { getQueue, type QueueItem } from "./workService";
import { listInbox, type WorkItem } from "./processService";

/** Toàn bộ việc đang mở của tôi; lịch không được bỏ sót hạn xử lý ở trang tiếp theo. */
export const getCalendarWork = async (
  workspace: string,
  signal?: AbortSignal,
): Promise<QueueItem[]> => {
  const first = await getQueue(workspace, { page: 0, size: 50 }, signal);
  const items = [...first.items];
  const pageSize = first.size > 0 ? first.size : 50;
  for (let page = 1; page * pageSize < first.total; page += 1) {
    const next = await getQueue(workspace, { page, size: pageSize }, signal);
    items.push(...next.items);
  }
  return [...new Map(items.map((item) => [item.id, item])).values()];
};

/** All open workspace work, including intake/approval tasks without an execution SLA. */
export const getCoordinationWork = async (
  workspace: string,
  signal?: AbortSignal,
): Promise<WorkItem[]> => {
  const first = await listInbox(workspace, "ALL", { page: 0, size: 100 }, signal);
  const items = [...first.items];
  const pageSize = first.size > 0 ? first.size : 100;
  for (let page = 1; page * pageSize < first.total; page += 1) {
    const next = await listInbox(workspace, "ALL", { page, size: pageSize }, signal);
    items.push(...next.items);
  }
  return [...new Map(items.map((item) => [item.id, item])).values()];
};
