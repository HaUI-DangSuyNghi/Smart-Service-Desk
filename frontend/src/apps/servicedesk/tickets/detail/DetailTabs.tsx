import { useState } from "react";
import { ViewTabs, type ViewTab } from "../../../../platform/shared/ui/ViewTabs";
import type { TicketView } from "../../services/ticketService";
import { CorrectionsList } from "../CorrectionsList";
import { TicketComments } from "./TicketComments";

type Tab = "comments" | "corrections";

interface Props {
  workspace: string;
  ticket: TicketView;
  /** Người gửi không thấy lịch sử sửa. */
  isRequesterView: boolean;
}

/** Dưới phần mô tả: Bình luận / Lịch sử sửa (kiểu Activity của Jira); chỉ còn một mục thì hiện thẳng, không tab. */
export const DetailTabs = ({ workspace, ticket, isRequesterView }: Props) => {
  const [tab, setTab] = useState<Tab>("comments");
  const hasCorrections = !isRequesterView && (ticket.corrections?.length ?? 0) > 0;
  const tabs: ViewTab<Tab>[] = [
    { key: "comments", label: "Bình luận" },
    ...(hasCorrections
      ? [{ key: "corrections" as const, label: "Lịch sử sửa", count: ticket.corrections?.length }]
      : []),
  ];
  const active = tabs.some((item) => item.key === tab) ? tab : "comments";

  return (
    <div>
      <div className="border-b border-crm-line px-3 py-1.5">
        {tabs.length > 1 ? (
          <ViewTabs label="Hoạt động của ticket" tabs={tabs} value={active} onChange={setTab} />
        ) : (
          <h2 className="px-1 py-0.5 font-medium">Bình luận</h2>
        )}
      </div>
      {active === "comments" && <TicketComments workspace={workspace} ticketId={ticket.id} />}
      {active === "corrections" && ticket.corrections && (
        <CorrectionsList corrections={ticket.corrections} />
      )}
    </div>
  );
};
