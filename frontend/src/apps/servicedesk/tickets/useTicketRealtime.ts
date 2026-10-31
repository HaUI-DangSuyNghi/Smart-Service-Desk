import type { QueryKey } from "@tanstack/react-query";
import {
  USER_EVENTS,
  useRealtimeInvalidation,
  workspaceTopic,
  type RealtimeEvent,
} from "../../../platform/shared/realtime";
import { ticketKeys } from "./useTickets";

/** Danh sách, board, hàng tiếp nhận, thống kê của workspace (chỉ query đang hiện mới tải lại). */
export const ticketListKeys = (workspace: string): QueryKey[] => [
  ticketKeys.all(workspace),
  ["servicedesk", "statistics", workspace],
];

/** Trang chi tiết: bình luận chỉ tải lại bình luận; đổi khác tải lại ticket, tiếp nhận, tiến độ. */
export const ticketDetailKeys = (workspace: string, id: string, event: RealtimeEvent): QueryKey[] =>
  event.type === "TICKET_COMMENTED"
    ? [ticketKeys.comments(workspace, id)]
    : [
        ticketKeys.detail(workspace, id),
        ["servicedesk", "intake", workspace, id],
        ["servicedesk", "context-revision", workspace, id],
        ["servicedesk", "canonical", workspace, id],
        // Tiến độ (timeline) thuộc khối P
        ["process", workspace],
      ];

/**
 * Trang danh sách / board / tiếp nhận: agent, coordinator nghe kênh chung của workspace
 * (server bỏ qua đăng ký của người gửi); người gửi nghe ticket của mình qua kênh riêng.
 */
export const useTicketListRealtime = (workspace: string | null) => {
  useRealtimeInvalidation(workspace ? workspaceTopic(workspace, "tickets") : null, () =>
    ticketListKeys(workspace!),
  );
  useRealtimeInvalidation(USER_EVENTS, (event) =>
    event.type === "TICKET_CHANGED" ? ticketListKeys(event.workspace) : [],
  );
};

/** Trang chi tiết: ai xem được ticket thì nghe được kênh của ticket đó. */
export const useTicketDetailRealtime = (workspace: string, id: string) =>
  useRealtimeInvalidation(
    workspaceTopic(workspace, `tickets/${encodeURIComponent(id)}`),
    (event) => ticketDetailKeys(workspace, id, event),
  );
