import { Link, useParams } from "react-router";
import { IconTicket } from "@tabler/icons-react";
import { useAuth } from "../../../platform/human/auth";
import { useCurrentWorkspace } from "../../../platform/human/workspace";
import { Page, PageBody, PageHeader } from "../../../platform/shared/ui/page";
import AssistancePanel from "../components/AssistancePanel";

export default function TicketDetailPage() {
  const { workspace, ticketId } = useParams();
  const { user, status } = useAuth();
  const { current } = useCurrentWorkspace();

  if (status !== "authenticated") return null;

  if (
    !workspace ||
    !/^[a-zA-Z0-9_-]{1,100}$/.test(workspace) ||
    !ticketId ||
    !/^[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}$/.test(ticketId)
  ) {
    return <p role="alert">Đường dẫn ticket không hợp lệ.</p>;
  }

  return (
    <Page>
      <PageHeader title="Hỗ trợ xử lý ticket" icon={IconTicket} />
      <PageBody>
        <div className="mx-auto max-w-3xl space-y-6 px-4 py-6">
          <Link
            to="/servicedesk/tickets"
            className="inline-flex min-h-11 items-center underline underline-offset-4"
          >
            Về danh sách ticket
          </Link>
          <div className="space-y-2">
            <p className="break-all text-sm text-muted-foreground">
              {workspace} · {ticketId}
            </p>
          </div>
          {current === workspace ? (
            <AssistancePanel
              key={`${workspace}/${ticketId}/${user?.profile.sub ?? ""}`}
              workspace={workspace}
              ticketId={ticketId}
            />
          ) : (
            <p role="status">Chọn đúng workspace của ticket để sử dụng trợ lý.</p>
          )}
        </div>
      </PageBody>
    </Page>
  );
}
