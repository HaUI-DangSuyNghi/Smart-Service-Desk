import { IconTicket } from "@tabler/icons-react";
import { Page, PageBody, PageHeader } from "../../../platform/shared/ui/page";

const TicketListPage = () => {
  return (
    <Page>
      <PageHeader title="Ticket" icon={IconTicket} />
      <PageBody>
        <p className="p-4 text-crm-text-2">Danh sách ticket sẽ hiển thị ở đây.</p>
      </PageBody>
    </Page>
  );
};

export default TicketListPage;
