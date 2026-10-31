import type { ComponentType } from "react";
import { cn } from "cn";
import type { TagTone } from "@/platform/shared/ui/Tag";

const COLORS: Record<TagTone, { card: string; icon: string }> = {
  blue: {
    card: "border-crm-tone-blue/20 from-crm-tone-blue-soft/70",
    icon: "bg-crm-tone-blue-soft text-crm-tone-blue",
  },
  green: {
    card: "border-crm-tone-green/20 from-crm-tone-green-soft/70",
    icon: "bg-crm-tone-green-soft text-crm-tone-green",
  },
  orange: {
    card: "border-crm-tone-orange/20 from-crm-tone-orange-soft/70",
    icon: "bg-crm-tone-orange-soft text-crm-tone-orange",
  },
  purple: {
    card: "border-crm-tone-purple/20 from-crm-tone-purple-soft/70",
    icon: "bg-crm-tone-purple-soft text-crm-tone-purple",
  },
  turquoise: {
    card: "border-crm-tone-turquoise/20 from-crm-tone-turquoise-soft/70",
    icon: "bg-crm-tone-turquoise-soft text-crm-tone-turquoise",
  },
  red: {
    card: "border-crm-tone-red/20 from-crm-tone-red-soft/70",
    icon: "bg-crm-tone-red-soft text-crm-tone-red",
  },
  gray: {
    card: "border-crm-line from-crm-tone-gray-soft/70",
    icon: "bg-crm-tone-gray-soft text-crm-tone-gray",
  },
};

interface Props {
  label: string;
  value: string;
  icon: ComponentType<{ size?: number; stroke?: number; className?: string }>;
  /** Dòng phụ giải thích con số, ví dụ "hiện tại" hay "trong 7 ngày". */
  hint?: string;
  tone?: TagTone;
}

/** Color marks the metric type; values remain dark for easy comparison. */
export const StatTile = ({ label, value, icon: Icon, hint, tone = "blue" }: Props) => {
  return (
    <div
      className={cn(
        "flex min-w-0 flex-col gap-2 rounded-2xl border bg-crm-surface bg-linear-to-br to-crm-surface p-4",
        COLORS[tone].card,
      )}
    >
      <span className="flex items-center gap-2 text-crm-sm font-medium text-crm-text-2">
        <span
          className={cn(
            "flex size-8 shrink-0 items-center justify-center rounded-xl",
            COLORS[tone].icon,
          )}
        >
          <Icon size={17} stroke={2} />
        </span>
        <span className="truncate" title={label}>
          {label}
        </span>
      </span>
      <span className="text-2xl font-semibold tracking-tight text-crm-text tabular-nums">
        {value}
      </span>
      {hint && (
        <span className="truncate text-crm-xs text-crm-text-3" title={hint}>
          {hint}
        </span>
      )}
    </div>
  );
};
