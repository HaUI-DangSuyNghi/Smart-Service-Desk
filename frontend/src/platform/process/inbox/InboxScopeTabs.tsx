import { cn } from "cn";
import type { InboxScope } from "@/platform/services";

const TABS: readonly { scope: InboxScope; label: string }[] = [
  { scope: "ASSIGNED", label: "Việc của tôi" },
  { scope: "CANDIDATE", label: "Có thể nhận" },
];

interface Props {
  scope: InboxScope;
  /** Tổng số việc theo từng view; chưa tải xong thì để trống. */
  counts: Partial<Record<InboxScope, number>>;
  onChange: (scope: InboxScope) => void;
}

/** Hai view của trang Công việc trên toolbar, kiểu chọn view của Twenty. */
export const InboxScopeTabs = ({ scope, counts, onChange }: Props) => {
  return (
    <div role="tablist" aria-label="Loại việc" className="flex items-center gap-1">
      {TABS.map((tab) => {
        const isActive = tab.scope === scope;
        return (
          <button
            key={tab.scope}
            type="button"
            role="tab"
            aria-selected={isActive}
            onClick={() => onChange(tab.scope)}
            className={cn(
              "flex h-9 items-center gap-1 rounded-crm-sm px-2 transition-colors duration-100 hover:bg-crm-hover md:h-7",
              isActive ? "bg-crm-hover font-medium text-crm-text" : "text-crm-text-2",
            )}
          >
            {tab.label}
            {counts[tab.scope] !== undefined && (
              <span className="font-normal text-crm-text-3 tabular-nums">{counts[tab.scope]}</span>
            )}
          </button>
        );
      })}
    </div>
  );
};
