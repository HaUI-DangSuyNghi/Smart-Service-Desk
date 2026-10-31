import type { InboxLabelLoader } from "../../../platform/process";
import { getTicket } from "../services/ticketService";

export const TicketInboxLabel: InboxLabelLoader = async (workspace, task, signal) =>
  (await getTicket(workspace, task.businessKey, signal)).title;
