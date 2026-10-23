import { useQuery } from "@tanstack/react-query";
import { getMyWorkspaces } from "@/platform/services";

/** Workspace của tôi kèm tên hiển thị và vai trò (GET /hpdi/v1/workspaces). */
export const useWorkspaces = () => {
  return useQuery({
    queryKey: ["workspaces"],
    queryFn: ({ signal }) => getMyWorkspaces(signal),
    staleTime: 5 * 60 * 1000,
  });
};
