import { useDeferredValue, useState } from "react";
import { useNavigate } from "react-router";
import {
  IconBook,
  IconCalendar,
  IconFileText,
  IconLock,
  IconList,
  IconSearch,
  IconTag,
  IconUser,
  IconWorld,
} from "@tabler/icons-react";
import { PERMISSIONS, useCurrentWorkspace, usePermissions } from "@/platform/human/workspace";
import type { KnowledgeSummary } from "@/platform/services";
import { formatDateTime } from "@/platform/shared/lib/format";
import { DataTable, type DataTableColumn } from "@/platform/shared/ui/DataTable";
import { EmptyState } from "@/platform/shared/ui/EmptyState";
import { ErrorState } from "@/platform/shared/ui/ErrorState";
import { Page, PageBody, PageHeader, PageToolbar } from "@/platform/shared/ui/page";
import { Pagination } from "@/platform/shared/ui/Pagination";
import { TableSkeleton } from "@/platform/shared/ui/Skeleton";
import { UploadKnowledgeDialog } from "./UploadKnowledgeDialog";
import { KnowledgeAccessTag } from "./KnowledgeAccessTag";
import { KNOWLEDGE_PAGE_SIZE, useKnowledgeList } from "./useKnowledge";

const COLUMNS: readonly DataTableColumn<KnowledgeSummary>[] = [
  { key: "title", header: "Tiêu đề", icon: IconFileText, width: "320px", cell: (row) => row.title },
  {
    key: "source",
    header: "Nguồn tài liệu",
    icon: IconWorld,
    width: "240px",
    cell: (row) => row.source,
  },
  {
    key: "version",
    header: "Phiên bản",
    icon: IconTag,
    width: "110px",
    cell: (row) => row.version,
  },
  {
    key: "usageRights",
    header: "Ai được xem?",
    icon: IconLock,
    width: "200px",
    cell: (row) => <KnowledgeAccessTag value={row.usageRights} />,
  },
  {
    key: "owner",
    header: "Người tạo",
    icon: IconUser,
    width: "180px",
    cell: (row) => row.ownerName ?? <span className="text-crm-text-3">—</span>,
  },
  {
    key: "createdAt",
    header: "Ngày tạo",
    icon: IconCalendar,
    width: "160px",
    cell: (row) => formatDateTime(row.createdAt),
  },
];

/** Knowledge Hub: tài liệu tri thức của workspace, kèm nguồn, phiên bản, quyền sử dụng. */
const KnowledgeListPage = () => {
  const navigate = useNavigate();
  const { current } = useCurrentWorkspace();
  const { can } = usePermissions();
  const [search, setSearch] = useState("");
  const [page, setPage] = useState(0);
  const query = useDeferredValue(search.trim());
  const list = useKnowledgeList(query, page);

  const total = list.data?.total ?? 0;
  const canUpload = can(PERMISSIONS.documentUpload);

  const handleSearch = (value: string) => {
    setSearch(value);
    setPage(0);
  };

  return (
    <Page>
      <PageHeader
        title="Tài liệu"
        icon={IconBook}
        actions={canUpload && <UploadKnowledgeDialog />}
      />
      <PageToolbar
        start={
          <span className="flex items-center gap-1 text-crm-text">
            <IconList size={16} stroke={2} className="text-crm-text-3" />
            Tất cả tài liệu
            <span className="text-crm-text-3">· {total}</span>
          </span>
        }
        end={
          <>
            {/* Điện thoại: ô tìm kiếm giãn hết hàng; màn rộng: cố định 224px */}
            <label className="flex h-8 min-w-0 flex-1 items-center gap-1 rounded-crm-sm border border-crm-line px-2 focus-within:border-crm-focus sm:h-7 sm:w-56 sm:flex-none">
              <IconSearch size={14} stroke={2} className="shrink-0 text-crm-text-3" />
              <input
                value={search}
                onChange={(event) => handleSearch(event.target.value)}
                placeholder="Tìm theo tiêu đề"
                maxLength={200}
                className="min-w-0 flex-1 bg-transparent outline-none placeholder:text-crm-text-3"
              />
            </label>
            <Pagination
              page={page}
              total={total}
              pageSize={KNOWLEDGE_PAGE_SIZE}
              onPageChange={setPage}
            />
          </>
        }
      />
      <PageBody>
        {current !== null && list.isPending && (
          <TableSkeleton columns={6} label="Đang tải tài liệu" />
        )}
        {current === null && (
          <EmptyState
            icon={IconBook}
            title="Chưa thuộc workspace nào"
            description="Knowledge Hub thuộc từng workspace."
          />
        )}
        {list.error && <ErrorState error={list.error} />}
        {list.data && list.data.items.length === 0 && (
          <EmptyState
            icon={IconBook}
            title={query ? "Không có tài liệu khớp" : "Chưa có tài liệu"}
            description={
              query ? "Thử từ khóa khác." : "Tài liệu hướng dẫn, quy trình nội bộ sẽ hiện ở đây."
            }
            action={canUpload && !query ? <UploadKnowledgeDialog /> : undefined}
          />
        )}
        {list.data && list.data.items.length > 0 && (
          <DataTable
            columns={COLUMNS}
            rows={list.data.items}
            getRowKey={(row) => row.id}
            onRowClick={(row) => navigate(`/knowledge/${row.id}`)}
          />
        )}
      </PageBody>
    </Page>
  );
};

export default KnowledgeListPage;
