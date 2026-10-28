import { useState } from "react";
import { AssistantPanel } from "../../../../platform/intelligence";
import { ViewTabs, type ViewTab } from "../../../../platform/shared/ui/ViewTabs";
import { askTicketAssistance, type TicketView } from "../../services/ticketService";
import { CorrectionsList } from "../CorrectionsList";

type Tab = "corrections" | "ask";

interface Props {
  workspace: string;
  ticket: TicketView;
  /** Người gửi không thấy lịch sử sửa. */
  isRequesterView: boolean;
}

/** Dưới phần mô tả: Lịch sử sửa / Hỏi AI (kiểu Activity của Jira); chỉ còn một mục thì hiện thẳng, không tab. */
export const DetailTabs = ({ workspace, ticket, isRequesterView }: Props) => {
  const [tab, setTab] = useState<Tab>("ask");
  const hasCorrections = !isRequesterView && (ticket.corrections?.length ?? 0) > 0;
  const tabs: ViewTab<Tab>[] = [
    ...(hasCorrections
      ? [{ key: "corrections" as const, label: "Lịch sử sửa", count: ticket.corrections?.length }]
      : []),
    { key: "ask", label: "Hỏi AI" },
  ];
  const active = tabs.some((item) => item.key === tab) ? tab : "ask";

  return (
    <div>
      <div className="border-b border-crm-line px-3 py-1.5">
        {tabs.length > 1 ? (
          <ViewTabs label="Hoạt động của ticket" tabs={tabs} value={active} onChange={setTab} />
        ) : (
          <h2 className="px-1 py-0.5 font-medium">Hỏi AI</h2>
        )}
      </div>
      {active === "corrections" && ticket.corrections && (
        <CorrectionsList corrections={ticket.corrections} />
      )}
      {active === "ask" && (
        <AssistantPanel
          ask={(question, signal) => askTicketAssistance(workspace, ticket.id, question, signal)}
          subjectLabel="ticket"
        />
      )}
    </div>
  );
};
