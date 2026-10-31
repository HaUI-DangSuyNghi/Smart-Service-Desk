import type { MouseEvent } from "react";
import { IconDots } from "@tabler/icons-react";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from "../../../../platform/shared/ui/dropdown-menu";
import type { TicketView } from "../../services/ticketService";
import type { BoardAction } from "./boardAccess";

const ACTION_LABELS: Readonly<Record<BoardAction, string>> = {
  intake: "Tiếp nhận",
  review: "Duyệt và giao việc",
  reassign: "Giao lại",
  correct: "Sửa phân loại / ưu tiên",
};

interface Props {
  ticket: TicketView;
  actions: readonly BoardAction[];
  onAction: (ticket: TicketView, action: BoardAction) => void;
}

/** Menu ⋯ trên thẻ: các lối tắt tới dialog đã có, không viết luật mới. */
export const CardMenu = ({ ticket, actions, onAction }: Props) => {
  // Thẻ bấm được để mở chi tiết: chặn cú bấm lan lên thẻ
  const stop = (event: MouseEvent) => event.stopPropagation();

  return (
    <DropdownMenu>
      <DropdownMenuTrigger
        aria-label="Thao tác với ticket"
        onClick={stop}
        className="-mt-0.5 -mr-1 flex size-6 shrink-0 items-center justify-center rounded-crm-sm text-crm-text-3 outline-none hover:bg-crm-press focus-visible:bg-crm-press data-[state=open]:bg-crm-press"
      >
        <IconDots size={16} stroke={2} />
      </DropdownMenuTrigger>
      <DropdownMenuContent align="end" className="w-auto min-w-44" onClick={stop}>
        {actions.map((action) => (
          <DropdownMenuItem key={action} onSelect={() => onAction(ticket, action)}>
            {ACTION_LABELS[action]}
          </DropdownMenuItem>
        ))}
      </DropdownMenuContent>
    </DropdownMenu>
  );
};
