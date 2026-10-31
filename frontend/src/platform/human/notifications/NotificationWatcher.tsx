import { useEffect, useRef } from "react";
import { USER_EVENTS, useRealtimeInvalidation } from "@/platform/shared/realtime";
import { toast } from "@/platform/shared/ui/toast";
import { useCurrentWorkspace } from "../workspace/useCurrentWorkspace";
import { notificationKey, useNotifications } from "./useNotifications";
import { useOpenNotification } from "./useOpenNotification";

/**
 * Đặt một lần trong khung app: thông báo mới (sau lần tải đầu) hiện toast ở mọi trang;
 * cảnh báo khẩn cấp dùng toast lỗi cho nổi bật. Thay cho toast cảnh báo cũ (cảnh báo đã nằm trong thông báo).
 */
export const NotificationWatcher = () => {
  const { current } = useCurrentWorkspace();
  // Cùng khoá truy vấn với chuông (size 10) nên không tải thêm
  const notifications = useNotifications({ size: 10 });
  // Có thông báo mới thì chuông tải lại ngay; toast hiện như cũ khi dữ liệu mới về
  useRealtimeInvalidation(USER_EVENTS, (event) =>
    event.type === "NOTIFICATION" ? [notificationKey(event.workspace)] : [],
  );
  const open = useOpenNotification();
  // id đã thấy theo workspace; lần đầu tải (hoặc vừa đổi workspace) không báo thông báo cũ
  const seen = useRef<{ workspace: string | null; ids: Set<string> } | null>(null);

  useEffect(() => {
    const items = notifications.data?.items;
    if (!items) return;
    if (seen.current === null || seen.current.workspace !== current) {
      seen.current = { workspace: current, ids: new Set(items.map((item) => item.id)) };
      return;
    }
    const known = seen.current.ids;
    items
      .filter((item) => !known.has(item.id) && !item.readAt)
      .forEach((item) => {
        known.add(item.id);
        const show =
          item.severity === "CRITICAL"
            ? toast.error
            : item.severity === "WARNING"
              ? toast.warning
              : toast.info;
        show(item.title, {
          description: item.body ?? undefined,
          action: { label: "Xem", onClick: () => open(item) },
        });
      });
  }, [notifications.data, current, open]);

  return null;
};
