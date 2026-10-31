import { useQuery } from "@tanstack/react-query";
import { PERMISSIONS, useCurrentWorkspace, usePermissions } from "@/platform/human/workspace";
import { getCoordinationWork } from "@/platform/services/workCalendarService";
import { useFallbackInterval } from "@/platform/shared/realtime";
import { processKeys } from "../common/queryKeys";
import { useInbox } from "./useInbox";

/** Điều phối chỉ đếm việc chờ tiếp nhận hoặc duyệt/giao; Agent đếm việc được giao. */
export const InboxBadge = () => {
  const { can } = usePermissions();
  const { current } = useCurrentWorkspace();
  const coordinating = can(PERMISSIONS.taskAssign);
  const interval = useFallbackInterval(30_000);
  const coordination = useQuery({
    queryKey: [...processKeys.all(current), "coordination-calendar"],
    queryFn: ({ signal }) => getCoordinationWork(current!, signal),
    enabled: current !== null && coordinating && can(PERMISSIONS.taskHandle),
    retry: false,
    refetchInterval: interval,
  });
  const assigned = useInbox("ASSIGNED", 0, !coordinating && can(PERMISSIONS.taskHandle));
  const count = coordinating
    ? (coordination.data?.filter(
        (task) => task.candidateGroups.length > 0 && ["intake", "review"].includes(task.key),
      ).length ?? 0)
    : (assigned.data?.total ?? 0);
  if (count === 0) return null;

  return (
    <span
      aria-label={`${count} ${coordinating ? "việc chờ tiếp nhận hoặc duyệt và giao" : "việc đang chờ bạn"}`}
      className="rounded-crm-sm bg-crm-muted px-1 text-crm-xs leading-4 font-medium text-crm-text-2 tabular-nums"
    >
      {count > 99 ? "99+" : count}
    </span>
  );
};
