import type { ReactNode } from "react";
import { PageSkeleton } from "@/platform/shared/ui/Skeleton";
import { hasAnyPermission } from "./permissions";
import { usePermissions } from "./usePermissions";

interface Props {
  /** Cần ít nhất một trong các quyền này ở workspace đang chọn. */
  permissions: readonly string[];
  /** Hiện khi không đủ quyền (shell truyền trang 403 vào). */
  fallback: ReactNode;
  children: ReactNode;
}

/** Chặn trang theo quyền (Keycloak) để giao diện gọn; backend vẫn tự kiểm quyền mọi request. */
export const PermissionGate = ({ permissions, fallback, children }: Props) => {
  const { granted, isPending } = usePermissions();

  if (isPending) return <PageSkeleton label="Đang kiểm tra quyền" />;
  return hasAnyPermission(granted, permissions) ? children : fallback;
};
