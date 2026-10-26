import { keepPreviousData, useQuery } from "@tanstack/react-query";
import { useCurrentWorkspace } from "../../../platform/human/workspace";
import { getTicket, listTickets } from "../services/ticketService";

export const TICKETS_PAGE_SIZE = 20;

const ticketKeys = {
  list: (workspace: string | null, page: number) =>
    ["servicedesk", "tickets", workspace, page] as const,
  detail: (workspace: string, id: string) => ["servicedesk", "ticket", workspace, id] as const,
};

/** Ticket của workspace đang chọn, mới nhất trước. */
export const useTicketList = (page: number) => {
  const { current } = useCurrentWorkspace();
  return useQuery({
    queryKey: ticketKeys.list(current, page),
    // `enabled` bảo đảm current khác null khi queryFn chạy
    queryFn: ({ signal }) => listTickets(current!, { page, size: TICKETS_PAGE_SIZE }, signal),
    enabled: current !== null,
    placeholderData: keepPreviousData,
  });
};

/** Một ticket; 404 khi không tồn tại hoặc người xem không được thấy. */
export const useTicket = (workspace: string, id: string) =>
  useQuery({
    queryKey: ticketKeys.detail(workspace, id),
    queryFn: ({ signal }) => getTicket(workspace, id, signal),
  });
