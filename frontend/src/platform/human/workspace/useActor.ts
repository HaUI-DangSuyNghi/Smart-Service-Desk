import { useQuery } from "@tanstack/react-query";
import { getActor } from "@/platform/services";
import { useCurrentWorkspace } from "./useCurrentWorkspace";

const NO_ROLES: readonly string[] = [];

/** Vai trò của người dùng trong workspace đang chọn (theo backend, GET .../actor). */
export const useActor = () => {
  const { current } = useCurrentWorkspace();
  const query = useQuery({
    queryKey: ["actor", current],
    // `enabled` bảo đảm current khác null khi queryFn chạy
    queryFn: ({ signal }) => getActor(current!, signal),
    enabled: current !== null,
    staleTime: 5 * 60 * 1000,
  });

  return {
    actor: query.data ?? null,
    roles: query.data?.roles ?? NO_ROLES,
    // Không có workspace thì không có gì để chờ
    isPending: current !== null && query.isPending,
    error: query.error,
  };
};
