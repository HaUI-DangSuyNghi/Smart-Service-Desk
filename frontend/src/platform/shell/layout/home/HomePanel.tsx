import type { ReactNode } from "react";
import { Link } from "react-router";
import { IconArrowRight } from "@tabler/icons-react";
import { Button } from "@/platform/shared/ui/button";
import { ErrorState } from "@/platform/shared/ui/ErrorState";
import { Skeleton } from "@/platform/shared/ui/Skeleton";
import type { MenuIcon } from "../../router/registry";

interface Props {
  title: string;
  icon: MenuIcon;
  to: string;
  total?: number;
  actions?: ReactNode;
  children: ReactNode;
}

export const HomePanel = ({ title, icon: Icon, to, total, actions, children }: Props) => (
  <section
    aria-label={title}
    className="min-w-0 overflow-hidden rounded-crm-md border border-crm-line bg-crm-surface"
  >
    <div className="flex flex-wrap items-center justify-between gap-x-3 gap-y-1 border-b border-crm-line px-4 py-2">
      <h2 className="flex min-w-0 items-center gap-2 font-medium text-crm-text">
        <Icon size={16} className="shrink-0 text-crm-text-2" />
        {title}
        {total !== undefined && (
          <span className="text-crm-sm font-normal tabular-nums text-crm-text-3">{total}</span>
        )}
      </h2>
      <Link
        to={to}
        aria-label={`Xem tất cả ${title.toLocaleLowerCase("vi")}`}
        className="inline-flex min-h-9 items-center gap-1 rounded-crm-sm text-crm-sm text-crm-text-2 hover:text-crm-accent focus-visible:outline-2 focus-visible:outline-crm-focus md:min-h-7"
      >
        Xem tất cả <IconArrowRight size={14} />
      </Link>
    </div>
    {actions}
    {children}
  </section>
);

interface ContentProps {
  isPending: boolean;
  error: unknown;
  isEmpty: boolean;
  emptyMessage: string;
  label: string;
  onRetry: () => void;
  children: ReactNode;
}

export const HomePanelContent = ({
  isPending,
  error,
  isEmpty,
  emptyMessage,
  label,
  onRetry,
  children,
}: ContentProps) => {
  if (isPending)
    return (
      <div role="status" aria-label={label} aria-busy className="divide-y divide-crm-line-light">
        {[0, 1, 2].map((row) => (
          <div key={row} className="flex flex-col gap-2 px-4 py-4">
            <Skeleton className="w-2/3" />
            <Skeleton className="h-2.5 w-1/3" />
          </div>
        ))}
      </div>
    );
  if (error)
    return (
      <div className="pb-4 text-center">
        <ErrorState error={error} />
        <Button variant="outline" size="sm" className="min-h-9 md:min-h-7" onClick={onRetry}>
          Thử lại
        </Button>
      </div>
    );
  if (isEmpty)
    return <p className="px-4 py-10 text-center text-crm-sm text-crm-text-3">{emptyMessage}</p>;
  return <div className="divide-y divide-crm-line-light">{children}</div>;
};
