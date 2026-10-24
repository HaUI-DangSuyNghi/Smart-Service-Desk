import { useQuery } from "@tanstack/react-query";
import { getMembers } from "@/platform/services";
import { ApiError } from "@/platform/shared/api";
import { useCurrentWorkspace } from "./useCurrentWorkspace";

/**
 * Danh bạ đầy đủ (kèm vai trò) của workspace đang chọn; cần quyền xem thành viên.
 * Chỉ cần tên của vài người thì dùng usePeopleNames.
 */
export const useMembers = (enabled = true) => {
  const { current } = useCurrentWorkspace();
  return useQuery({
    queryKey: ["members", current],
    // `enabled` bảo đảm current khác null khi queryFn chạy
    queryFn: ({ signal }) => getMembers(current!, signal),
    enabled: current !== null && enabled,
    staleTime: 60 * 1000,
    // Danh bạ không phản hồi (503) thì thôi, không gọi lại liên tục
    retry: (count, error) => !(error instanceof ApiError && error.status === 503) && count < 2,
  });
};
