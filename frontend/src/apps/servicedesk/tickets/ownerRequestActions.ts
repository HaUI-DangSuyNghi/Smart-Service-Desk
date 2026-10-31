import type { RequestAction } from "@/platform/process/requests/RequestActions";
import type { TicketView } from "../services/ticketService";

/** Owner actions only; the shared dialog checks current application policy with the API. */
export const ownerRequestActions = (
  ticket: TicketView,
  subject: string | null | undefined,
): RequestAction[] => {
  if (
    ticket.creator !== subject ||
    !ticket.taskId ||
    !ticket.state ||
    ["CLOSED", "CANCELLED", "FOLLOWING", "UNCLASSIFIED"].includes(ticket.state)
  )
    return [];
  if (
    ["INTAKE", "AWAITING_REVIEW"].includes(ticket.state) &&
    ticket.classification?.source !== "COORDINATOR"
  )
    return ["edit", "cancel"];
  return ["close"];
};
