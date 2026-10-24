import { useAssignedTaskCount } from "./useInbox";

/** Số việc của tôi cạnh mục "Công việc" ở sidebar; không có việc thì không hiện. */
export const InboxBadge = () => {
  const count = useAssignedTaskCount();
  if (count === 0) return null;

  return (
    <span
      aria-label={`${count} việc đang chờ bạn`}
      className="rounded-crm-sm bg-crm-muted px-1 text-crm-xs leading-4 font-medium text-crm-text-2 tabular-nums"
    >
      {count > 99 ? "99+" : count}
    </span>
  );
};
