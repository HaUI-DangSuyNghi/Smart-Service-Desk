import type { ReactNode } from "react";
import { Loading } from "@/platform/shared/ui/Loading";
import { hasAnyRole } from "./roles";
import { useActor } from "./useActor";

interface Props {
  /** Cần ít nhất một trong các vai trò này ở workspace đang chọn. */
  roles: readonly string[];
  /** Hiện khi không đủ quyền (shell truyền trang 403 vào). */
  fallback: ReactNode;
  children: ReactNode;
}

/** Chặn trang theo vai trò để giao diện gọn; backend vẫn tự kiểm quyền mọi request. */
export const RoleGate = ({ roles, fallback, children }: Props) => {
  const { roles: actorRoles, isPending } = useActor();

  if (isPending) {
    return (
      <div className="relative h-full">
        <Loading label="Đang kiểm tra quyền" />
      </div>
    );
  }
  return hasAnyRole(actorRoles, roles) ? children : fallback;
};
