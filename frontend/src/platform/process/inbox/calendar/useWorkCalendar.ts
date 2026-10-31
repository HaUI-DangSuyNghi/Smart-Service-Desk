import { useQuery } from "@tanstack/react-query";
import { useCurrentWorkspace } from "@/platform/human/workspace";
import { getCalendarWork } from "@/platform/services";
import { useFallbackInterval } from "@/platform/shared/realtime";
import { processKeys } from "../../common/queryKeys";

export const useWorkCalendar = () => {
  const { current } = useCurrentWorkspace();
  const interval = useFallbackInterval(30_000);
  return useQuery({
    queryKey: [...processKeys.all(current), "calendar"],
    queryFn: ({ signal }) => getCalendarWork(current!, signal),
    enabled: current !== null,
    refetchInterval: interval,
  });
};
