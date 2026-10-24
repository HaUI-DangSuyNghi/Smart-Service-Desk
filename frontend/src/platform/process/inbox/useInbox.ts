import { keepPreviousData, useQuery } from "@tanstack/react-query";
import { useCurrentWorkspace } from "@/platform/human/workspace";
import { listInbox, type InboxScope } from "@/platform/services";
import { processKeys } from "../common/queryKeys";

export const INBOX_PAGE_SIZE = 20;

// Việc mới do người khác tạo ra, nên tự tải lại định kỳ (và khi quay lại tab, mặc định của TanStack)
const REFRESH_INTERVAL_MS = 30 * 1000;

export const useInbox = (scope: InboxScope, page: number) => {
  const { current } = useCurrentWorkspace();
  return useQuery({
    queryKey: processKeys.inbox(current, scope, page),
    // `enabled` bảo đảm current khác null khi queryFn chạy
    queryFn: ({ signal }) => listInbox(current!, scope, { page, size: INBOX_PAGE_SIZE }, signal),
    enabled: current !== null,
    // Đổi trang vẫn giữ bảng cũ, tránh chớp skeleton
    placeholderData: keepPreviousData,
    refetchInterval: REFRESH_INTERVAL_MS,
  });
};

/** Số việc đang giao cho tôi; dùng chung cache với trang đầu của view "Việc của tôi". */
export const useAssignedTaskCount = () => useInbox("ASSIGNED", 0).data?.total ?? 0;
