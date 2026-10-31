import { useState } from "react";
import { useNavigate } from "react-router";
import { IconInbox } from "@tabler/icons-react";
import { PERMISSIONS, useCurrentWorkspace, usePermissions } from "@/platform/human/workspace";
import type { InboxScope, WorkItem } from "@/platform/services";
import { EmptyState } from "@/platform/shared/ui/EmptyState";
import { ErrorState } from "@/platform/shared/ui/ErrorState";
import { Page, PageBody, PageHeader, PageToolbar } from "@/platform/shared/ui/page";
import { Pagination } from "@/platform/shared/ui/Pagination";
import { SelectField } from "@/platform/shared/ui/SelectField";
import { TableSkeleton } from "@/platform/shared/ui/Skeleton";
import { toast, toastError } from "@/platform/shared/ui/toast";
import { processLink } from "../common/processLinks";
import { AssignTaskDialog } from "../tasks/AssignTaskDialog";
import { CompleteTaskDialog } from "../tasks/CompleteTaskDialog";
import { claimErrorMessage } from "../tasks/taskErrors";
import { useClaimTask } from "../tasks/useTaskActions";
import { InboxTable } from "./InboxTable";
import { INBOX_PAGE_SIZE, useInbox } from "./useInbox";

const SCOPE_LABELS: Record<InboxScope, string> = {
  ASSIGNED: "Việc của tôi",
  CANDIDATE: "Có thể nhận",
  ALL: "Tất cả việc",
};

const EMPTY_TEXT: Record<InboxScope, { title: string; description: string }> = {
  ASSIGNED: {
    title: "Không có việc nào đang chờ bạn",
    description: "Nhận việc ở mục “Có thể nhận”, hoặc chờ yêu cầu mới.",
  },
  CANDIDATE: {
    title: "Không có việc nào để nhận",
    description: "Việc của nhóm bạn chưa ai nhận sẽ hiện ở đây.",
  },
  ALL: {
    title: "Không có việc nào đang mở",
    description: "Mọi việc đang chờ xử lý trong workspace sẽ hiện ở đây để bạn giao việc.",
  },
};

/** Trang Công việc chung cho mọi quy trình: việc của tôi, việc nhóm tôi có thể nhận, và (điều phối viên) tất cả việc. */
const InboxPage = () => {
  const navigate = useNavigate();
  const { current } = useCurrentWorkspace();
  const { can } = usePermissions();
  const canAssign = can(PERMISSIONS.taskAssign);
  const scopes: InboxScope[] = canAssign
    ? ["ASSIGNED", "CANDIDATE", "ALL"]
    : ["ASSIGNED", "CANDIDATE"];

  const [selectedScope, setScope] = useState<InboxScope>("ASSIGNED");
  // Đổi sang workspace không có quyền giao việc thì tab "Tất cả việc" không còn: quay về việc của tôi
  const scope = scopes.includes(selectedScope) ? selectedScope : "ASSIGNED";
  const [page, setPage] = useState(0);
  const [completing, setCompleting] = useState<WorkItem | null>(null);
  const [assigning, setAssigning] = useState<WorkItem | null>(null);
  const claim = useClaimTask();

  // Tải mọi view được xem: số trên tab luôn đúng và đổi tab không phải chờ
  const pageOf = (item: InboxScope) => (scope === item ? page : 0);
  const views: Record<InboxScope, ReturnType<typeof useInbox>> = {
    ASSIGNED: useInbox("ASSIGNED", pageOf("ASSIGNED")),
    CANDIDATE: useInbox("CANDIDATE", pageOf("CANDIDATE")),
    ALL: useInbox("ALL", pageOf("ALL"), canAssign),
  };
  const inbox = views[scope];
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
      {/* Không đặt nút "Tạo yêu cầu" ở đây: yêu cầu mình tạo nằm ở trang "Yêu cầu" */}
      <PageHeader title="Công việc" icon={IconInbox} />
      <PageToolbar
        start={
          <SelectField
            aria-label="Loại việc"
            options={scopes.map((item) => {
              const total = views[item].data?.total;
              return {
                value: item,
                label:
                  total === undefined ? SCOPE_LABELS[item] : `${SCOPE_LABELS[item]} (${total})`,
              };
            })}
            value={scope}
            onValueChange={(value) => handleScopeChange(value as InboxScope)}
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
            onAssign={setAssigning}
            // Như link mã: mở trang chi tiết của app; quy trình không có trang riêng mới mở tiến độ chung
            onOpen={(task) =>
              navigate(
                processLink(task.processKey, task.businessKey) ??
                  `/processes/${task.processInstanceId}`,
              )
            }
          />
        )}
      </PageBody>
      <CompleteTaskDialog task={completing} onClose={() => setCompleting(null)} />
      <AssignTaskDialog task={assigning} onClose={() => setAssigning(null)} />
    </Page>
  );
};

export default InboxPage;
