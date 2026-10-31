import { getQueue, type QueueItem } from "./workService";

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
