import { useQuery } from "@tanstack/react-query";
import { getMyPermissions } from "@/platform/services";
import { useCurrentWorkspace } from "./useCurrentWorkspace";

/** Quyền của tôi ở workspace đang chọn, kèm mô tả tiếng Việt từ Keycloak. */
export const useMyPermissions = () => {
  const { current } = useCurrentWorkspace();
  return useQuery({
    queryKey: ["permissions", current],
    // `enabled` bảo đảm current khác null khi queryFn chạy
    queryFn: ({ signal }) => getMyPermissions(current!, signal),
    enabled: current !== null,
    // Backend cache danh bạ Keycloak 60 giây; quyền ít đổi
    staleTime: 60 * 1000,
  });
};
