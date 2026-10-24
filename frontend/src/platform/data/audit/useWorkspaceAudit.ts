import { keepPreviousData, useQuery } from "@tanstack/react-query";
import { useCurrentWorkspace } from "@/platform/human/workspace";
import { getWorkspaceAudit, type AuditFilter } from "@/platform/services";

export const AUDIT_PAGE_SIZE = 20;

/** Nhật ký của cả workspace theo bộ lọc (điều phối viên), mới nhất trước. */
export const useWorkspaceAudit = (filter: Omit<AuditFilter, "size">) => {
  const { current } = useCurrentWorkspace();
  return useQuery({
    queryKey: ["audit", current, filter],
    // `enabled` bảo đảm current khác null khi queryFn chạy
    queryFn: ({ signal }) =>
      getWorkspaceAudit(current!, { ...filter, size: AUDIT_PAGE_SIZE }, signal),
    enabled: current !== null,
    placeholderData: keepPreviousData,
  });
};
