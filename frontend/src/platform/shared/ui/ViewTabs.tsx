import { cn } from "cn";

export interface ViewTab<Key extends string> {
  key: Key;
  label: string;
  /** Số bên cạnh nhãn; chưa có thì không hiện. */
  count?: number;
}

interface Props<Key extends string> {
  tabs: readonly ViewTab<Key>[];
  value: Key;
  onChange: (key: Key) => void;
  /** Tên nhóm tab cho trình đọc màn hình. */
  label: string;
}

/** Các view của một trang danh sách trên toolbar (kiểu chọn view của Twenty). */
export const ViewTabs = <Key extends string>({ tabs, value, onChange, label }: Props<Key>) => {
  return (
    <div role="tablist" aria-label={label} className="flex flex-wrap items-center gap-1">
      {tabs.map((tab) => {
        const isActive = tab.key === value;
        return (
          <button
            key={tab.key}
            type="button"
            role="tab"
            aria-selected={isActive}
            onClick={() => onChange(tab.key)}
            className={cn(
              "flex h-9 items-center gap-1 rounded-crm-sm px-2 transition-colors duration-100 hover:bg-crm-hover md:h-7",
              isActive ? "bg-crm-hover font-medium text-crm-text" : "text-crm-text-2",
            )}
          >
            {tab.label}
            {tab.count !== undefined && (
              <span className="font-normal text-crm-text-3 tabular-nums">{tab.count}</span>
            )}
          </button>
        );
      })}
    </div>
  );
};
