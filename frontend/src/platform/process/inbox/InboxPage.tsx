import { useState } from "react";
import { useNavigate } from "react-router";
import { IconInbox } from "@tabler/icons-react";
import { useCurrentWorkspace } from "@/platform/human/workspace";
import type { InboxScope, WorkItem } from "@/platform/services";
import { EmptyState } from "@/platform/shared/ui/EmptyState";
import { ErrorState } from "@/platform/shared/ui/ErrorState";
import { Page, PageBody, PageHeader, PageToolbar } from "@/platform/shared/ui/page";
import { Pagination } from "@/platform/shared/ui/Pagination";
import { TableSkeleton } from "@/platform/shared/ui/Skeleton";
import { toast, toastError } from "@/platform/shared/ui/toast";
import { CompleteTaskDialog } from "../tasks/CompleteTaskDialog";
import { claimErrorMessage } from "../tasks/taskErrors";
import { useClaimTask } from "../tasks/useTaskActions";
import { InboxScopeTabs } from "./InboxScopeTabs";
import { InboxTable } from "./InboxTable";
import { INBOX_PAGE_SIZE, useInbox } from "./useInbox";

const EMPTY_TEXT: Record<InboxScope, { title: string; description: string }> = {
  ASSIGNED: {
    title: "Không có việc nào đang chờ bạn",
    description: "Nhận việc ở mục “Có thể nhận”, hoặc chờ yêu cầu mới.",
  },
  CANDIDATE: {
    title: "Không có việc nào để nhận",
    description: "Việc của nhóm bạn chưa ai nhận sẽ hiện ở đây.",
  },
};

/** Công việc chung cho mọi quy trình: việc của tôi và việc nhóm tôi có thể nhận. */
const InboxPage = () => {
  const navigate = useNavigate();
  const { current } = useCurrentWorkspace();
  const [scope, setScope] = useState<InboxScope>("ASSIGNED");
  const [page, setPage] = useState(0);
  const [completing, setCompleting] = useState<WorkItem | null>(null);
  const claim = useClaimTask();

  // Tải cả hai view: số trên tab luôn đúng và đổi tab không phải chờ
  const assigned = useInbox("ASSIGNED", scope === "ASSIGNED" ? page : 0);
  const candidate = useInbox("CANDIDATE", scope === "CANDIDATE" ? page : 0);
  const inbox = scope === "ASSIGNED" ? assigned : candidate;
  const items = inbox.data?.items ?? [];

  const handleScopeChange = (next: InboxScope) => {
    setScope(next);
    setPage(0);
  };

  const handleClaim = (task: WorkItem) => {
    claim.mutate(task, {
      onSuccess: () =>
        toast.success("Đã nhận việc", {
          description: "Việc đã chuyển sang “Việc của tôi”.",
          action: { label: "Xem", onClick: () => handleScopeChange("ASSIGNED") },
        }),
      onError: (error) => toastError(claimErrorMessage(error), error),
    });
  };

  return (
    <Page>
      {/* Không đặt nút "Tạo yêu cầu" ở đây: yêu cầu mình tạo nằm ở trang "Yêu cầu", công việc chỉ có việc người khác giao */}
      <PageHeader title="Công việc" icon={IconInbox} />
      <PageToolbar
        start={
          <InboxScopeTabs
            scope={scope}
            counts={{ ASSIGNED: assigned.data?.total, CANDIDATE: candidate.data?.total }}
            onChange={handleScopeChange}
          />
        }
        end={
          inbox.data && (
            <Pagination
              page={page}
              total={inbox.data.total}
              pageSize={INBOX_PAGE_SIZE}
              onPageChange={setPage}
            />
          )
        }
      />
      <PageBody>
        {current === null && <EmptyState icon={IconInbox} title="Chưa thuộc workspace nào" />}
        {current !== null && inbox.isPending && (
          <TableSkeleton columns={5} label="Đang tải công việc" />
        )}
        {inbox.error && <ErrorState error={inbox.error} />}
        {inbox.data && items.length === 0 && (
          <EmptyState
            icon={IconInbox}
            title={EMPTY_TEXT[scope].title}
            description={EMPTY_TEXT[scope].description}
          />
        )}
        {items.length > 0 && (
          <InboxTable
            scope={scope}
            items={items}
            claimingId={claim.isPending ? claim.variables?.id : undefined}
            onClaim={handleClaim}
            onComplete={setCompleting}
            onOpen={(task) => navigate(`/processes/${task.processInstanceId}`)}
          />
        )}
      </PageBody>
      <CompleteTaskDialog task={completing} onClose={() => setCompleting(null)} />
    </Page>
  );
};

export default InboxPage;
