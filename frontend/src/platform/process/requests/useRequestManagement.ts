import { useQuery } from "@tanstack/react-query";
import { useCurrentWorkspace } from "@/platform/human/workspace";
import { readRequestManagement } from "@/platform/services/requestLifecycleService";
import { processKeys } from "../common/queryKeys";

export const useRequestManagement = (processId: string, enabled = true) => {
  const { current } = useCurrentWorkspace();
  return useQuery({
    queryKey: [...processKeys.all(current), "request-management", processId],
    enabled: enabled && Boolean(current),
    queryFn: ({ signal }) => readRequestManagement(current!, processId, signal),
    retry: false,
  });
};
