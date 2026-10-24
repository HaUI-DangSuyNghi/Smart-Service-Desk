import { keepPreviousData, useQuery } from "@tanstack/react-query";
import { useCurrentWorkspace } from "@/platform/human/workspace";
import { getProcessStatistics } from "@/platform/services";
import { viewerZone, type DateRange } from "./dashboardRange";

/** Số liệu quy trình của workspace trong khoảng thời gian (điều phối viên). */
export const useProcessStatistics = (range: DateRange) => {
  const { current } = useCurrentWorkspace();
  return useQuery({
    queryKey: ["dashboard", current, range.from, range.to],
    // `enabled` bảo đảm current khác null khi queryFn chạy
    queryFn: ({ signal }) =>
      getProcessStatistics(current!, { ...range, zone: viewerZone() }, signal),
    enabled: current !== null,
    // Đổi khoảng thời gian vẫn giữ số cũ, tránh chớp skeleton
    placeholderData: keepPreviousData,
    // Việc chờ nhận thay đổi theo người khác, nên tự tải lại định kỳ
    refetchInterval: 60 * 1000,
  });
};
