import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { processLink } from "@/platform/process";
import { useFallbackInterval } from "@/platform/shared/realtime";
import {
  listNotifications,
  readAllNotifications,
  readNotification,
  type NotificationItem,
} from "@/platform/services";
import { useCurrentWorkspace } from "../workspace/useCurrentWorkspace";

// Thông báo mới được đẩy qua WebSocket; polling chỉ dự phòng khi mất kết nối
const POLL_INTERVAL_MS = 30 * 1000;
export const notificationKey = (workspace: string | null) => ["notifications", workspace] as const;

/** Thông báo của tôi (mọi vai trò), mới nhất trước; tự tải lại định kỳ cho chuông và toast. */
export const useNotifications = ({ unreadOnly = false, page = 0, size = 20 } = {}) => {
  const { current } = useCurrentWorkspace();
  const interval = useFallbackInterval(POLL_INTERVAL_MS);
  return useQuery({
    queryKey: [...notificationKey(current), unreadOnly, page, size],
    // `enabled` bảo đảm current khác null khi queryFn chạy
    queryFn: ({ signal }) => listNotifications(current!, { unreadOnly, page, size }, signal),
    enabled: current !== null,
    refetchInterval: interval,
  });
};

const useNotificationMutation = <Input>(
  action: (workspace: string, input: Input) => Promise<void>,
) => {
  const { current } = useCurrentWorkspace();
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (input: Input) => {
      if (!current) throw new Error("Chưa chọn workspace");
      return action(current, input);
    },
    onSettled: () => queryClient.invalidateQueries({ queryKey: notificationKey(current) }),
  });
};

export const useReadNotification = () =>
  useNotificationMutation((workspace, id: string) => readNotification(workspace, id));

export const useReadAllNotifications = () =>
  useNotificationMutation<void>((workspace) => readAllNotifications(workspace));

/** Trang mở khi bấm thông báo: link có sẵn, không thì trang chi tiết của app theo quy trình. */
export const notificationHref = (item: NotificationItem): string | null =>
  item.link ??
  (item.processKey && item.businessKey ? processLink(item.processKey, item.businessKey) : null);
