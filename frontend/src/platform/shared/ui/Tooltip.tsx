import type { ReactElement, ReactNode } from "react";
import { Tooltip as TooltipPrimitive } from "radix-ui";

interface Props {
  content: ReactNode;
  /** Một phần tử nhận được hover/focus. */
  children: ReactElement;
}

/** Chú thích nhỏ hiện khi rê chuột hoặc focus; nằm trong portal nên không bị cột cuộn cắt. */
export const Tooltip = ({ content, children }: Props) => {
  return (
    <TooltipPrimitive.Provider delayDuration={100} skipDelayDuration={300}>
      <TooltipPrimitive.Root>
        <TooltipPrimitive.Trigger asChild>{children}</TooltipPrimitive.Trigger>
        <TooltipPrimitive.Portal>
          <TooltipPrimitive.Content
            side="top"
            sideOffset={4}
            className="z-50 max-w-60 rounded-crm-sm bg-crm-text px-2 py-1 text-crm-sm text-crm-surface"
          >
            {content}
          </TooltipPrimitive.Content>
        </TooltipPrimitive.Portal>
      </TooltipPrimitive.Root>
    </TooltipPrimitive.Provider>
  );
};
