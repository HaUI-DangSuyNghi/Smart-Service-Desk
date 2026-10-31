import type { ReactNode } from "react";
import { cn } from "cn";

export type TagTone = "gray" | "blue" | "red" | "green" | "orange" | "purple" | "turquoise";

// Viết đủ tên class để Tailwind sinh ra được
const TONE_CLASS: Record<TagTone, string> = {
  gray: "bg-crm-tone-gray-soft text-crm-tone-gray",
  blue: "bg-crm-tone-blue-soft text-crm-tone-blue",
  red: "bg-crm-tone-red-soft text-crm-tone-red",
  green: "bg-crm-tone-green-soft text-crm-tone-green",
  orange: "bg-crm-tone-orange-soft text-crm-tone-orange",
  purple: "bg-crm-tone-purple-soft text-crm-tone-purple",
  turquoise: "bg-crm-tone-turquoise-soft text-crm-tone-turquoise",
};

interface Props {
  tone?: TagTone;
  children: ReactNode;
}

/** Nhãn kiểu Twenty: cao 20px, bo 4px, nền nhạt + chữ đậm cùng tông. */
export const Tag = ({ tone = "gray", children }: Props) => {
  return (
    <span
      className={cn(
        "inline-flex h-5 items-center rounded-crm-sm px-1.5 text-crm-sm font-medium whitespace-nowrap",
        TONE_CLASS[tone],
      )}
    >
      {children}
    </span>
  );
};
