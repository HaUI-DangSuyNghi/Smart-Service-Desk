import { IconUserCircle } from "@tabler/icons-react";
import {
  PERMISSIONS,
  usePermissions,
  useCurrentWorkspace,
  useMe,
  useWorkspaces,
  roleLabel,
  roleTone,
} from "@/platform/human/workspace";
import type { WorkspaceSummary } from "@/platform/services";
import { DataTable, type DataTableColumn } from "@/platform/shared/ui/DataTable";
import { EmptyState } from "@/platform/shared/ui/EmptyState";
import { ErrorState } from "@/platform/shared/ui/ErrorState";
import { Page, PageBody, PageHeader } from "@/platform/shared/ui/page";
import { FieldsSkeleton } from "@/platform/shared/ui/Skeleton";
import { Tag } from "@/platform/shared/ui/Tag";
import { MyActivity } from "../activity/MyActivity";
import { MyPermissions } from "./MyPermissions";

type WorkspaceRow = WorkspaceSummary;

const COLUMNS: readonly DataTableColumn<WorkspaceRow>[] = [
  { key: "name", header: "Workspace", width: "200px", cell: (row) => row.name },
  { key: "code", header: "Mã", width: "120px", cell: (row) => row.workspace },
  {
    key: "roles",
    header: "Vai trò",
    cell: (row) => (
      <span className="flex gap-1">
        {row.roles.map((role) => (
          <Tag key={role} tone={roleTone(role)}>
            {roleLabel(role)}
          </Tag>
        ))}
      </span>
    ),
  },
];

/** Người dùng / nhóm / quyền (chữ H): thông tin tài khoản và vai trò ở từng workspace. */
const ProfilePage = () => {
  const { data: me, isPending, error } = useMe();
  const { current, currentName } = useCurrentWorkspace();
  const { can } = usePermissions();

  const { data: workspaces } = useWorkspaces();
  const rows: WorkspaceRow[] = workspaces ?? [];

  return (
    <Page>
      <PageHeader title="Tài khoản" icon={IconUserCircle} />
      <PageBody>
        {isPending && <FieldsSkeleton rows={4} label="Đang tải tài khoản" />}
        {error && <ErrorState error={error} />}
        {me && (
          <div className="flex flex-col gap-6 p-4">
            <section className="flex items-center gap-3">
              <span className="grid size-10 place-items-center rounded-full bg-crm-muted text-crm-lg font-medium">
                {me.username.charAt(0).toUpperCase()}
              </span>
              <div className="flex flex-col">
                <span className="font-medium">{me.username}</span>
                <span className="text-crm-sm text-crm-text-3">
                  {me.platformAdmin ? "Quản trị nền tảng" : "Thành viên"}
                  {currentName && ` · đang làm việc ở ${currentName}`}
                </span>
              </div>
            </section>

            <section className="flex flex-col gap-2">
              <h2 className="font-medium">Workspace và vai trò</h2>
              {rows.length > 0 ? (
                <div className="overflow-x-auto rounded-crm-sm border border-crm-line">
                  <DataTable columns={COLUMNS} rows={rows} getRowKey={(row) => row.workspace} />
                </div>
              ) : (
                <EmptyState
                  icon={IconUserCircle}
                  title="Chưa thuộc workspace nào"
                  description="Liên hệ quản trị để được thêm vào workspace."
                />
              )}
            </section>

            {current !== null && <MyPermissions />}

            {/* Hoạt động theo workspace đang chọn; không thuộc workspace nào thì không có */}
            {current !== null && can(PERMISSIONS.auditViewOwn) && <MyActivity />}
          </div>
        )}
      </PageBody>
    </Page>
  );
};

export default ProfilePage;
