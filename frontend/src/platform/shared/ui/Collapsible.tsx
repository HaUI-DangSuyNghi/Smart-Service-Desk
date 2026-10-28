import type { ReactNode } from "react";
import { IconChevronDown } from "@tabler/icons-react";
import { cn } from "cn";
import { Collapsible as CollapsiblePrimitive } from "radix-ui";

interface Props {
  title: string;
  defaultOpen?: boolean;
  children: ReactNode;
  className?: string;
}

/** Khối có tiêu đề bấm để thu gọn/mở ra; viền dưới như các khối khác của trang chi tiết. */
export const Collapsible = ({ title, defaultOpen = true, children, className }: Props) => {
  return (
    <CollapsiblePrimitive.Root
      defaultOpen={defaultOpen}
      className={cn("border-b border-crm-line", className)}
    >
      <CollapsiblePrimitive.Trigger className="group flex w-full items-center gap-1 px-4 py-2.5 text-left font-medium outline-none hover:bg-crm-hover focus-visible:bg-crm-hover">
        <IconChevronDown
          size={14}
          stroke={2}
          className="shrink-0 text-crm-text-3 transition-transform duration-100 group-data-[state=closed]:-rotate-90 motion-reduce:transition-none"
        />
        {title}
      </CollapsiblePrimitive.Trigger>
      <CollapsiblePrimitive.Content className="px-4 pb-3">{children}</CollapsiblePrimitive.Content>
    </CollapsiblePrimitive.Root>
  );
};
