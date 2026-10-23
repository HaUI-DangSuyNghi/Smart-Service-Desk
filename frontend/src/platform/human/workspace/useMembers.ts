import { useQuery } from "@tanstack/react-query";
import { getMembers } from "@/platform/services";
import { ApiError } from "@/platform/shared/api";
import { useCurrentWorkspace } from "./useCurrentWorkspace";

/** Thành viên workspace đang chọn (danh bạ). */
export const useMembers = () => {
  const { current } = useCurrentWorkspace();
  return useQuery({
    queryKey: ["members", current],
    // `enabled` bảo đảm current khác null khi queryFn chạy
    queryFn: ({ signal }) => getMembers(current!, signal),
    enabled: current !== null,
    staleTime: 60 * 1000,
    // Danh bạ không phản hồi (503) thì thôi, không gọi lại liên tục
    retry: (count, error) => !(error instanceof ApiError && error.status === 503) && count < 2,
  });
};

/** subject → tên hiển thị (họ tên, không có thì username), để hiện "người tạo", "người xử lý". */
export const useMemberNames = (): ReadonlyMap<string, string> => {
  const { data } = useMembers();
  return new Map(
    (data ?? []).map((member) => [member.subject, member.fullName ?? member.username]),
  );
};
