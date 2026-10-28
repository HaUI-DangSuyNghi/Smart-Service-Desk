import { Link, useParams } from "react-router";
import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { IconArrowLeft, IconTicket } from "@tabler/icons-react";
import { useAuth } from "../../../platform/human/auth";
import {
  PERMISSIONS,
  useActor,
  useCurrentWorkspace,
  usePermissions,
} from "../../../platform/human/workspace";
import { SuggestionCard } from "../../../platform/intelligence";
import { Button } from "../../../platform/shared/ui/button";
import { ErrorState } from "../../../platform/shared/ui/ErrorState";
import { Page, PageBody, PageHeader } from "../../../platform/shared/ui/page";
import { FieldsSkeleton } from "../../../platform/shared/ui/Skeleton";
import { triageTicket } from "../services/ticketService";
import { AssessmentCard } from "../tickets/AssessmentCard";
import { DetailsCard } from "../tickets/detail/DetailsCard";
import { DetailTabs } from "../tickets/detail/DetailTabs";
import { ProgressCard } from "../tickets/detail/ProgressCard";
import { StatusBlock } from "../tickets/detail/StatusBlock";
import { ticketCode } from "../tickets/ticketLabels";
import { TicketSummary } from "../tickets/TicketSummary";
import { useTicket } from "../tickets/useTickets";
import { EnrichmentPanel } from "../tickets/EnrichmentPanel";
import { IntakePanel } from "../tickets/IntakePanel";
import { getIntake } from "../services/intakeService";

const WORKSPACE_PATTERN = /^[a-zA-Z0-9_-]{1,100}$/;
const UUID_PATTERN = /^[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}$/;

// Nhãn theo profile service-desk của AI
const SUGGESTION_LABELS = {
  category: "Loại sự cố",
  priority: "Mức ưu tiên",
  suggestedGroup: "Nhóm xử lý",
};
const EVIDENCE_LABELS = { title: "Tiêu đề", description: "Mô tả" };

/**
 * Chi tiết ticket kiểu Jira: trái là nội dung, kết quả AI, lịch sử sửa, hỏi AI; phải là trạng thái, hành động, bảng Chi tiết và Tiến độ (thu gọn, mở sẵn cho người gửi).
 * Link từ inbox không có workspace trong đường dẫn nên dùng workspace đang chọn.
 */
const TicketDetailPage = () => {
  const params = useParams();
  const { user, status } = useAuth();
  const { current } = useCurrentWorkspace();
  const workspace = params.workspace ?? current;
  const ticketId = params.ticketId;

  if (status !== "authenticated") return null;
  if (!workspace || !WORKSPACE_PATTERN.test(workspace) || !ticketId || !UUID_PATTERN.test(ticketId))
    return <p role="alert">Đường dẫn ticket không hợp lệ.</p>;

  return (
    <Page>
      <PageHeader
        title="Chi tiết ticket"
        icon={IconTicket}
        actions={
          <Button variant="outline" size="sm" asChild>
            <Link to="/servicedesk/tickets" aria-label="Về danh sách ticket">
              <IconArrowLeft size={14} stroke={2} />
              {/* Điện thoại chỉ giữ mũi tên để tiêu đề còn chỗ */}
              <span className="hidden sm:inline">Ticket</span>
            </Link>
          </Button>
        }
      />
      <PageBody>
        {current === workspace ? (
          // Đổi ticket hoặc người dùng thì tạo lại toàn bộ để không còn kết quả AI cũ
          <TicketDetail
            key={`${workspace}/${ticketId}/${user?.profile.sub ?? ""}`}
            workspace={workspace}
            ticketId={ticketId}
          />
        ) : (
          <p role="status" className="px-4 py-4">
            Chọn đúng workspace của ticket để xem chi tiết.
          </p>
        )}
      </PageBody>
    </Page>
  );
};

const TicketDetail = ({ workspace, ticketId }: { workspace: string; ticketId: string }) => {
  const ticket = useTicket(workspace, ticketId);
  const { actor } = useActor();
  const { can } = usePermissions();
  // Người gửi không xử lý việc: không cần thấy tải, ước tính, lịch sử sửa
  const isRequesterView = !can(PERMISSIONS.taskHandle) && !can(PERMISSIONS.taskAssign);
  const assessment = ticket.data?.assessment;
  const { user } = useAuth();
  const intake = useQuery({
    queryKey: ["servicedesk", "intake", workspace, ticketId, user?.profile.sub],
    queryFn: ({ signal }) => getIntake(workspace, ticketId, signal),
    retry: false,
    refetchInterval: (query) =>
      query.state.data && !["REVIEWED", "CANONICAL_FOLLOWER"].includes(query.state.data.status)
        ? 5000
        : false,
  });
  const [contextRevision, setContextRevision] = useState<number | null>(null);
  // Gọi AI tay chỉ khi chưa có kết quả lưu sẵn, không qua luồng tiếp nhận và chưa bổ sung ngữ cảnh
  const manualTriage = intake.isSuccess && !intake.data && contextRevision === 0;

  return (
    // Màn rộng: hai cột cố định chiều cao, mỗi cột tự cuộn (cột phải đứng yên khi đọc nội dung bên trái);
    // điện thoại xếp dọc và cả trang cuộn
    // (trạng thái và hành động lên trước, rồi đến nội dung)
    <div className="flex flex-col lg:grid lg:h-full lg:grid-cols-[minmax(0,1fr)_20rem]">
      {ticket.isPending && (
        <div className="lg:col-span-2">
          <FieldsSkeleton rows={5} label="Đang tải ticket" />
        </div>
      )}
      {ticket.error && (
        <div className="lg:col-span-2">
          <ErrorState error={ticket.error} fallback="Không tải được ticket." />
        </div>
      )}
      {ticket.data && (
        <>
          <div className="order-2 min-w-0 lg:order-1 lg:overflow-y-auto">
            <div className="border-b border-crm-line px-4 py-3">
              <p className="mb-1 text-crm-sm text-crm-text-3">
                <Link to="/servicedesk/tickets" className="hover:underline">
                  Ticket
                </Link>
                {" › "}
                <span className="tabular-nums">{ticketCode(ticket.data.id)}</span>
              </p>
              <h2 className="text-crm-xl font-semibold wrap-break-word">{ticket.data.title}</h2>
            </div>
            <TicketSummary ticket={ticket.data} />
            {/* Có kết quả AI đã lưu (chạy nền lúc tạo) thì hiện cho người xử lý; backend cũ chưa có thì gọi tay */}
            {assessment
              ? !isRequesterView && <AssessmentCard assessment={assessment} />
              : manualTriage && (
                  <SuggestionCard
                    fetchSuggestion={() => triageTicket(workspace, ticketId)}
                    fieldLabels={SUGGESTION_LABELS}
                    evidenceLabels={EVIDENCE_LABELS}
                  />
                )}
            <DetailTabs
              workspace={workspace}
              ticket={ticket.data}
              isRequesterView={isRequesterView}
            />
          </div>
          <aside className="order-1 min-w-0 border-b border-crm-line lg:order-2 lg:overflow-x-hidden lg:overflow-y-auto lg:border-b-0 lg:border-l">
            <StatusBlock ticket={ticket.data} />
            {/* Tiếp nhận và bổ sung thông tin: người gửi cũng cần thấy để trả lời câu hỏi của AI */}
            {intake.error && (
              <ErrorState error={intake.error} fallback="Chưa tải được trạng thái tiếp nhận." />
            )}
            {intake.data && (
              <IntakePanel
                workspace={workspace}
                ticketId={ticketId}
                state={intake.data}
                independent={ticket.data.creator !== user?.profile.sub}
              />
            )}
            {intake.isSuccess && (
              <EnrichmentPanel
                workspace={workspace}
                ticketId={ticketId}
                onRevisionChange={setContextRevision}
                managedIntake={!!intake.data}
              />
            )}
            <DetailsCard
              ticket={ticket.data}
              isRequesterView={isRequesterView && actor?.subject === ticket.data.creator}
            />
            <ProgressCard ticket={ticket.data} defaultOpen={isRequesterView} />
          </aside>
        </>
      )}
    </div>
  );
};

export default TicketDetailPage;
