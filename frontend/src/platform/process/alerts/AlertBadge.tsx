import { useAlerts } from "./useAlerts";

/** Số cảnh báo chưa đọc cạnh mục "Cảnh báo" ở sidebar; không có thì không hiện. */
export const AlertBadge = () => {
  const count = useAlerts().data?.unread ?? 0;
  if (count === 0) return null;

  return (
    <span
      aria-label={`${count} cảnh báo chưa đọc`}
      className="rounded-crm-sm bg-crm-danger-soft px-1 text-crm-xs leading-4 font-medium text-crm-danger tabular-nums"
    >
      {count > 99 ? "99+" : count}
    </span>
  );
};
