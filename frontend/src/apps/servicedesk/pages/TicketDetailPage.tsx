import { Link, useParams } from "react-router";
import { IconArrowLeft, IconTicket } from "@tabler/icons-react";
import { useAuth } from "../../../platform/human/auth";
import { useCurrentWorkspace } from "../../../platform/human/workspace";
import { AssistantPanel, SuggestionCard } from "../../../platform/intelligence";
import { Button } from "../../../platform/shared/ui/button";
import { ErrorState } from "../../../platform/shared/ui/ErrorState";
import { Page, PageBody, PageHeader } from "../../../platform/shared/ui/page";
import { FieldsSkeleton } from "../../../platform/shared/ui/Skeleton";
import { askTicketAssistance, triageTicket } from "../services/ticketService";
import { TicketProgress } from "../tickets/TicketProgress";
import { TicketSummary } from "../tickets/TicketSummary";
import { useTicket } from "../tickets/useTickets";

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
 * Chi tiết ticket: nội dung, tiến độ (khối P) và hỗ trợ của AI (khối I).
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

  return (
    // Màn rộng: trái là ticket + tiến độ, phải là AI; điện thoại xếp dọc
    <div className="lg:grid lg:grid-cols-[minmax(0,1fr)_minmax(0,26rem)]">
      <div className="min-w-0">
        {ticket.isPending && <FieldsSkeleton rows={5} label="Đang tải ticket" />}
        {ticket.error && <ErrorState error={ticket.error} fallback="Không tải được ticket." />}
        {ticket.data && (
          <>
            <h2 className="border-b border-crm-line px-4 py-3 text-crm-lg font-semibold wrap-break-word">
              {ticket.data.title}
            </h2>
            <TicketSummary ticket={ticket.data} />
            <TicketProgress processInstanceId={ticket.data.process.processInstanceId} />
          </>
        )}
      </div>
      <aside className="min-w-0 border-t border-crm-line lg:border-t-0 lg:border-l">
        <SuggestionCard
          fetchSuggestion={() => triageTicket(workspace, ticketId)}
          fieldLabels={SUGGESTION_LABELS}
          evidenceLabels={EVIDENCE_LABELS}
        />
        <AssistantPanel
          ask={(question, signal) => askTicketAssistance(workspace, ticketId, question, signal)}
          subjectLabel="ticket"
        />
      </aside>
    </div>
  );
};

export default TicketDetailPage;
