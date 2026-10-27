import { Tag } from "../../../platform/shared/ui/Tag";
import type { TicketState } from "../services/ticketService";
import { TICKET_STATE_LABELS, TICKET_STATE_TONES } from "./ticketLabels";

/** Trạng thái ticket theo R6. */
export const TicketStateTag = ({ state }: { state: TicketState }) => {
  return <Tag tone={TICKET_STATE_TONES[state]}>{TICKET_STATE_LABELS[state]}</Tag>;
};
