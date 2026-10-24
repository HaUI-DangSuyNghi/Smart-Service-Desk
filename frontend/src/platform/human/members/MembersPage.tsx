import { IconId, IconUser, IconUsers, IconUserShield } from "@tabler/icons-react";
import { useCurrentWorkspace, useMembers, roleLabel, roleTone } from "@/platform/human/workspace";
import type { WorkspaceMember } from "@/platform/services";
import { DataTable, type DataTableColumn } from "@/platform/shared/ui/DataTable";
import { EmptyState } from "@/platform/shared/ui/EmptyState";
import { ErrorState } from "@/platform/shared/ui/ErrorState";
import { Page, PageBody, PageHeader, PageToolbar } from "@/platform/shared/ui/page";
import { TableSkeleton } from "@/platform/shared/ui/Skeleton";
import { Tag } from "@/platform/shared/ui/Tag";

const COLUMNS: readonly DataTableColumn<WorkspaceMember>[] = [
  {
    key: "username",
    header: "Tài khoản",
    icon: IconUser,
    width: "200px",
    cell: (row) => row.username,
  },
  {
    key: "fullName",
    header: "Họ tên",
    icon: IconId,
    width: "240px",
    cell: (row) => row.fullName ?? <span className="text-crm-text-3">—</span>,
  },
  {
    key: "roles",
    header: "Vai trò",
    icon: IconUserShield,
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

/** Người dùng / nhóm (chữ H): thành viên workspace đang chọn và vai trò của họ. */
const MembersPage = () => {
  const { current, currentName } = useCurrentWorkspace();
  const members = useMembers();
  const count = members.data?.length ?? 0;

  return (
    <Page>
      <PageHeader title="Thành viên" icon={IconUsers} />
      <PageToolbar
        start={
          <span className="flex items-center gap-1 text-crm-text">
            {currentName ?? "Chưa chọn workspace"}
            <span className="text-crm-text-3">· {count}</span>
          </span>
        }
      />
      <PageBody>
        {current !== null && members.isPending && (
          <TableSkeleton columns={3} label="Đang tải danh bạ" />
        )}
        {current === null && <EmptyState icon={IconUsers} title="Chưa thuộc workspace nào" />}
        {members.error && <ErrorState error={members.error} />}
        {members.data && members.data.length === 0 && (
          <EmptyState icon={IconUsers} title="Workspace chưa có thành viên" />
        )}
        {members.data && members.data.length > 0 && (
          <DataTable columns={COLUMNS} rows={members.data} getRowKey={(row) => row.subject} />
        )}
      </PageBody>
    </Page>
  );
};

export default MembersPage;
