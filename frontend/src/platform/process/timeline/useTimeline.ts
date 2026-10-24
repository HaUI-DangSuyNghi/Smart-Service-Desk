import { useQuery } from "@tanstack/react-query";
import { useCurrentWorkspace } from "@/platform/human/workspace";
import { getTimeline } from "@/platform/services";
import { processKeys } from "../common/queryKeys";

/** Lịch sử một quy trình; 404 khi không tồn tại hoặc người xem chưa tham gia. */
export const useTimeline = (processInstanceId: string) => {
  const { current } = useCurrentWorkspace();
  return useQuery({
    queryKey: processKeys.timeline(current, processInstanceId),
    // `enabled` bảo đảm current khác null khi queryFn chạy
    queryFn: ({ signal }) => getTimeline(current!, processInstanceId, signal),
    enabled: current !== null && processInstanceId !== "",
  });
};
