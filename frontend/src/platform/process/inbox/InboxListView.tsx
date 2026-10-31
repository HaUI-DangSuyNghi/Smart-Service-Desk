import { useState } from "react";
import { useNavigate, useSearchParams } from "react-router";
import { IconInbox } from "@tabler/icons-react";
import { cn } from "cn";
import { PERMISSIONS, useCurrentWorkspace, usePermissions } from "@/platform/human/workspace";
import type { InboxScope, WorkItem } from "@/platform/services";
import { EmptyState } from "@/platform/shared/ui/EmptyState";
import { ErrorState } from "@/platform/shared/ui/ErrorState";
import { PageBody, PageToolbar } from "@/platform/shared/ui/page";
import { Pagination } from "@/platform/shared/ui/Pagination";
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

const EMPTY_TEXT: Record<InboxScope, string> = {
  ASSIGNED: "Không có việc nào đang chờ bạn",
  CANDIDATE: "Không có việc nào để nhận",
  ALL: "Không có việc nào đang mở",
};

/** Trang Công việc chung cho mọi quy trình: việc của tôi, việc nhóm tôi có thể nhận, và (điều phối viên) tất cả việc. */
export const InboxListView = () => {
  const navigate = useNavigate();
  const [params] = useSearchParams();
  const { current } = useCurrentWorkspace();
  const { can } = usePermissions();
  const canAssign = can(PERMISSIONS.taskAssign);
  const scopes: InboxScope[] = canAssign
    ? ["ALL", "ASSIGNED", "CANDIDATE"]
    : ["ASSIGNED", "CANDIDATE"];

  const [selectedScope, setScope] = useState<InboxScope>(() => {
    const initial = params.get("scope");
    return initial === "ASSIGNED" || initial === "CANDIDATE" || initial === "ALL"
      ? initial
      : canAssign
        ? "ALL"
        : "ASSIGNED";
  });
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
    <>
      <PageToolbar
        start={
          <div
            role="group"
            aria-label="Loại việc"
            className="flex min-w-0 flex-wrap items-center gap-1"
          >
            {scopes.map((item) => (
              <button
                key={item}
                type="button"
                aria-pressed={scope === item}
                onClick={() => handleScopeChange(item)}
                className={cn(
                  "inline-flex min-h-9 items-center gap-1.5 rounded-crm-sm px-2.5 text-crm-sm outline-none transition-colors duration-100 focus-visible:ring-2 focus-visible:ring-crm-focus md:min-h-7",
                  scope === item
                    ? "bg-crm-accent-soft font-medium text-crm-accent"
                    : "text-crm-text-2 hover:bg-crm-hover",
                )}
              >
                {SCOPE_LABELS[item]}
                {views[item].isSuccess && (
                  <span className="text-crm-xs tabular-nums opacity-70">
                    {views[item].data.total}
                  </span>
                )}
              </button>
            ))}
          </div>
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
        {!inbox.error && inbox.data && items.length === 0 && (
          <EmptyState icon={IconInbox} title={EMPTY_TEXT[scope]} />
        )}
        {!inbox.error && items.length > 0 && (
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
    </>
  );
};
