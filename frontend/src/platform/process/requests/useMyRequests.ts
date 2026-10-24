import { keepPreviousData, useQuery } from "@tanstack/react-query";
import { useCurrentWorkspace } from "@/platform/human/workspace";
import { listMyRequests } from "@/platform/services";
import { processKeys } from "../common/queryKeys";

export const REQUESTS_PAGE_SIZE = 20;

/** Yêu cầu tôi đã tạo (mọi quy trình), mới nhất trước. */
export const useMyRequests = (page: number) => {
  const { current } = useCurrentWorkspace();
  return useQuery({
    queryKey: processKeys.requests(current, page),
    // `enabled` bảo đảm current khác null khi queryFn chạy
    queryFn: ({ signal }) => listMyRequests(current!, { page, size: REQUESTS_PAGE_SIZE }, signal),
    enabled: current !== null,
    placeholderData: keepPreviousData,
  });
};
