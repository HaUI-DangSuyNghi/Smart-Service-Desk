import { keepPreviousData, useQuery } from "@tanstack/react-query";
import { useCurrentWorkspace } from "@/platform/human/workspace";
import { listInbox, type InboxScope } from "@/platform/services";
import { useFallbackInterval } from "@/platform/shared/realtime";
import { processKeys } from "../common/queryKeys";

export const INBOX_PAGE_SIZE = 20;

// Việc mới được đẩy qua WebSocket; polling chỉ dự phòng khi mất kết nối (tab ẩn thì tạm dừng)
const REFRESH_INTERVAL_MS = 30 * 1000;

/** `enabled = false` khi người dùng không được xem view này (ví dụ "Tất cả việc" cần quyền giao việc). */
export const useInbox = (scope: InboxScope, page: number, enabled = true) => {
  const { current } = useCurrentWorkspace();
  const interval = useFallbackInterval(REFRESH_INTERVAL_MS);
  return useQuery({
    queryKey: processKeys.inbox(current, scope, page),
    // `enabled` bảo đảm current khác null khi queryFn chạy
    queryFn: ({ signal }) => listInbox(current!, scope, { page, size: INBOX_PAGE_SIZE }, signal),
    enabled: current !== null && enabled,
    // Đổi trang vẫn giữ bảng cũ, tránh chớp skeleton
    placeholderData: keepPreviousData,
    refetchInterval: interval,
  });
};

/** Số việc đang giao cho tôi; dùng chung cache với trang đầu của view "Việc của tôi". */
export const useAssignedTaskCount = () => useInbox("ASSIGNED", 0).data?.total ?? 0;
