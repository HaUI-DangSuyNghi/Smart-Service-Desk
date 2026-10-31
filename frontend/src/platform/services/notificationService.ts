import { apiClient } from "@/platform/shared/api";
import { hpdiWorkspace } from "./paths";

// Kiểu khớp contracts/hpdi/openapi.yaml (NotificationItem, NotificationInbox)

/** NOTICE: đổi trạng thái của quy trình; ALERT: bản sao cảnh báo (quá hạn, khẩn cấp...). */
export type NotificationCategory = "NOTICE" | "ALERT";
export type NotificationSeverity = "INFO" | "WARNING" | "CRITICAL";

export interface NotificationItem {
  id: string;
  /** Mã loại: TICKET_CREATED, TICKET_ASSIGNED, SLA_BREACHED... */
  type: string;
  category: NotificationCategory;
  severity: NotificationSeverity;
  title: string;
  /** Thường là tiêu đề ticket hoặc ca trực liên quan. */
  body: string | null;
  /** Đường dẫn trong web; null thì tra trang chi tiết của app theo processKey + businessKey. */
  link: string | null;
  processKey: string | null;
  businessKey: string | null;
  createdAt: string;
  readAt: string | null;
}

export interface NotificationInbox {
  items: NotificationItem[];
  page: number;
  size: number;
  total: number;
  unread: number;
}

const path = (workspace: string) => `${hpdiWorkspace(workspace)}/notifications`;

/** GET thông báo của tôi, mới nhất trước. */
export const listNotifications = async (
  workspace: string,
  {
    unreadOnly = false,
    page = 0,
    size = 20,
  }: { unreadOnly?: boolean; page?: number; size?: number },
  signal?: AbortSignal,
): Promise<NotificationInbox> => {
  const { data } = await apiClient.get<NotificationInbox>(path(workspace), {
    params: { unreadOnly, page, size },
    signal,
  });
  return data;
};

/** POST đánh dấu đã đọc; gọi lặp vẫn thành công. */
export const readNotification = async (workspace: string, id: string): Promise<void> => {
  await apiClient.post(`${path(workspace)}/${encodeURIComponent(id)}/read`);
};

export const readAllNotifications = async (workspace: string): Promise<void> => {
  await apiClient.post(`${path(workspace)}/read-all`);
};
