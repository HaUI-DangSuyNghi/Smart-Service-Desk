import { useNavigate } from "react-router";
import type { NotificationItem } from "@/platform/services";
import { notificationHref, useReadNotification } from "./useNotifications";

/** Bấm một thông báo: đánh dấu đã đọc rồi mở trang liên quan (nếu có). */
export const useOpenNotification = () => {
  const navigate = useNavigate();
  const read = useReadNotification();
  return (item: NotificationItem) => {
    if (!item.readAt) read.mutate(item.id);
    const href = notificationHref(item);
    if (href) navigate(href);
  };
};
