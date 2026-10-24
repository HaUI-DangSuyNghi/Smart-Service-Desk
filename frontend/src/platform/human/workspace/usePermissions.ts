import { useMemo } from "react";
import { hasAnyPermission } from "./permissions";
import { useCurrentWorkspace } from "./useCurrentWorkspace";
import { useMyPermissions } from "./useMyPermissions";

const NO_PERMISSIONS: ReadonlySet<string> = new Set();

/**
 * Quyền của tôi ở workspace đang chọn để ẩn/hiện giao diện; backend vẫn tự kiểm quyền.
 * Không tải được quyền (Keycloak lỗi) thì coi như không có quyền nào: thà ẩn bớt còn hơn hiện nhầm.
 */
export const usePermissions = () => {
  const { current } = useCurrentWorkspace();
  const query = useMyPermissions();
  const granted = useMemo(
    () => (query.data ? new Set(query.data.map((permission) => permission.key)) : NO_PERMISSIONS),
    [query.data],
  );

  return {
    granted,
    can: (...required: string[]) => hasAnyPermission(granted, required),
    // Không có workspace thì không có gì để chờ
    isPending: current !== null && query.isPending,
  };
};
