import type { QueryKey } from "@tanstack/react-query";

/**
 * Gom khóa cache trong một khoảng ngắn rồi làm mới một lần (bỏ trùng):
 * duyệt hàng loạt sinh nhiều sự kiện liền nhau thì chỉ tải lại mỗi khóa một lần.
 */
export const createKeyBatcher = (flush: (keys: QueryKey[]) => void, delayMs: number) => {
  const pending = new Map<string, QueryKey>();
  let timer: ReturnType<typeof setTimeout> | undefined;
  return {
    add: (keys: readonly QueryKey[]) => {
      keys.forEach((key) => pending.set(JSON.stringify(key), key));
      if (timer !== undefined || pending.size === 0) return;
      timer = setTimeout(() => {
        timer = undefined;
        const batch = [...pending.values()];
        pending.clear();
        flush(batch);
      }, delayMs);
    },
    cancel: () => {
      if (timer !== undefined) clearTimeout(timer);
      timer = undefined;
      pending.clear();
    },
  };
};
