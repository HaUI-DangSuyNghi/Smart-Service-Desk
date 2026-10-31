import { IconCircleCheck, IconLock } from "@tabler/icons-react";
import { EmptyState } from "@/platform/shared/ui/EmptyState";
import { ErrorState } from "@/platform/shared/ui/ErrorState";
import { TextSkeleton } from "@/platform/shared/ui/Skeleton";
import { useCurrentWorkspace, useMyPermissions } from "../workspace";

/** Quyền của tôi ở workspace đang chọn, viết tiếng Việt theo mô tả khai báo trong Keycloak. */
export const MyPermissions = () => {
  const { currentName } = useCurrentWorkspace();
  const permissions = useMyPermissions();

  return (
    <section className="flex flex-col gap-2">
      <h2 className="font-medium">Quyền của bạn ở {currentName}</h2>
      {permissions.isPending && <TextSkeleton lines={3} label="Đang tải quyền" />}
      {permissions.error && (
        <ErrorState error={permissions.error} fallback="Không tải được danh sách quyền." />
      )}
      {permissions.data && permissions.data.length === 0 && (
        <EmptyState
          icon={IconLock}
          title="Chưa có quyền nào"
          description="Liên hệ quản trị để được cấp quyền trong workspace này."
        />
      )}
      {permissions.data && permissions.data.length > 0 && (
        <ul className="grid grid-cols-1 gap-x-4 gap-y-1 sm:grid-cols-2">
          {permissions.data.map((permission) => (
            <li key={permission.key} className="flex min-h-8 items-center gap-2">
              <IconCircleCheck size={16} stroke={2} className="shrink-0 text-crm-tone-green" />
              <span className="min-w-0 truncate text-crm-text">{permission.label}</span>
              {/* Tên quyền gốc, để đối chiếu khi trao đổi với quản trị */}
              <span className="shrink-0 font-mono text-crm-xs text-crm-text-3">
                {permission.key}
              </span>
            </li>
          ))}
        </ul>
      )}
    </section>
  );
};
