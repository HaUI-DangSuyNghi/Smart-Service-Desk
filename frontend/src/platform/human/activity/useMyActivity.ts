import { keepPreviousData, useQuery } from "@tanstack/react-query";
import { getMyActivity } from "@/platform/services";
import { useCurrentWorkspace } from "../workspace";

export const ACTIVITY_PAGE_SIZE = 20;

/** Hoạt động của tôi ở workspace đang chọn, mới nhất trước. */
export const useMyActivity = (page: number) => {
  const { current } = useCurrentWorkspace();
  return useQuery({
    queryKey: ["activity", current, page],
    // `enabled` bảo đảm current khác null khi queryFn chạy
    queryFn: ({ signal }) => getMyActivity(current!, { page, size: ACTIVITY_PAGE_SIZE }, signal),
    enabled: current !== null,
    placeholderData: keepPreviousData,
  });
};
