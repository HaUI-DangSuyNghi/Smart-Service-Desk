import { PERMISSIONS, usePermissions } from "@/platform/human/workspace";
import { useInbox } from "./useInbox";

/** Điều phối đếm việc đang mở trong workspace; Agent đếm việc được giao cho mình. */
export const InboxBadge = () => {
  const { can } = usePermissions();
  const coordinating = can(PERMISSIONS.taskAssign);
  const count = useInbox(coordinating ? "ALL" : "ASSIGNED", 0).data?.total ?? 0;
  if (count === 0) return null;

  return (
    <span
      aria-label={`${count} ${coordinating ? "việc đang mở" : "việc đang chờ bạn"}`}
      className="rounded-crm-sm bg-crm-muted px-1 text-crm-xs leading-4 font-medium text-crm-text-2 tabular-nums"
    >
      {count > 99 ? "99+" : count}
    </span>
  );
};
