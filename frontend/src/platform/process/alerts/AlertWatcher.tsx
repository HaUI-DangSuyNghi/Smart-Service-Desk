import { useEffect, useRef } from "react";
import { useNavigate } from "react-router";
import { PERMISSIONS, useCurrentWorkspace, usePermissions } from "@/platform/human/workspace";
import { toast } from "@/platform/shared/ui/toast";
import { processLink } from "../common/processLinks";
import { ALERT_LABELS } from "./alertLabels";
import { useAlerts } from "./useAlerts";

/**
 * Đặt một lần trong khung app: cảnh báo mới (sau lần tải đầu) hiện toast,
 * khẩn cấp thì toast lỗi nổi bật (R7: báo ngay, không chờ người dùng mở hàng đợi).
 */
export const AlertWatcher = () => {
  const navigate = useNavigate();
  const { current } = useCurrentWorkspace();
  const { can } = usePermissions();
  const alerts = useAlerts(can(PERMISSIONS.taskHandle));
  // id đã thấy theo workspace; lần đầu tải (hoặc vừa đổi workspace) không báo cảnh báo cũ
  const seen = useRef<{ workspace: string | null; ids: Set<string> } | null>(null);

  useEffect(() => {
    const items = alerts.data?.items;
    if (!items) return;
    if (seen.current === null || seen.current.workspace !== current) {
      seen.current = { workspace: current, ids: new Set(items.map((alert) => alert.id)) };
      return;
    }
    const known = seen.current.ids;
    items
      .filter((alert) => !known.has(alert.id) && !alert.readAt)
      .forEach((alert) => {
        known.add(alert.id);
        const open = () =>
          navigate(
            processLink(alert.processKey, alert.businessKey) ??
              `/processes/${alert.processInstanceId}`,
          );
        const show = alert.severity === "CRITICAL" ? toast.error : toast.warning;
        show(ALERT_LABELS[alert.type], {
          description: alert.label,
          action: { label: "Xem", onClick: open },
        });
      });
  }, [alerts.data, current, navigate]);

  return null;
};
