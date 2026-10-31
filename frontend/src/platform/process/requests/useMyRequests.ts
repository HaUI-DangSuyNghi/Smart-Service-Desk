import { keepPreviousData, useQuery } from "@tanstack/react-query";
import { useCurrentWorkspace } from "@/platform/human/workspace";
import { listRequests, type RequestScope } from "@/platform/services";
import { processKeys } from "../common/queryKeys";

export const REQUESTS_PAGE_SIZE = 20;

/** Yêu cầu của tôi (mine) hoặc mọi yêu cầu tôi được xem (all, điều phối viên), mới nhất trước. */
export const useRequests = (scope: RequestScope, page: number, enabled = true) => {
  const { current } = useCurrentWorkspace();
  return useQuery({
    queryKey: processKeys.requests(current, scope, page),
    // `enabled` bảo đảm current khác null khi queryFn chạy
    queryFn: ({ signal }) =>
      listRequests(current!, scope, { page, size: REQUESTS_PAGE_SIZE }, signal),
    enabled: current !== null && enabled,
    placeholderData: keepPreviousData,
  });
};
