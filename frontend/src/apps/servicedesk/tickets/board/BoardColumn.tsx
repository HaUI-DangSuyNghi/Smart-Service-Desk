import { useState } from "react";
import { useDroppable } from "@dnd-kit/core";
import { IconChevronRight } from "@tabler/icons-react";
import { cn } from "cn";
import { usePeopleNames } from "../../../../platform/human/workspace";
import { Button } from "../../../../platform/shared/ui/button";
import { ErrorState } from "../../../../platform/shared/ui/ErrorState";
import { Skeleton } from "../../../../platform/shared/ui/Skeleton";
import type { TicketView } from "../../services/ticketService";
import { TICKETS_PAGE_SIZE, useTicketList, type TicketFilter } from "../useTickets";
import type { BoardAction } from "./boardAccess";
import type { BoardColumnDef } from "./boardColumns";
import { TicketCard } from "./TicketCard";

// Viết đủ tên class để Tailwind sinh ra được
const DOT_CLASS: Record<BoardColumnDef["tone"], string> = {
  gray: "bg-crm-tone-gray",
  blue: "bg-crm-tone-blue",
  red: "bg-crm-tone-red",
  green: "bg-crm-tone-green",
  orange: "bg-crm-tone-orange",
  purple: "bg-crm-tone-purple",
  turquoise: "bg-crm-tone-turquoise",
};

/** Khi đang kéo thẻ: cột nhận được (viền nét đứt), cột không nhận (mờ), hoặc chưa kéo gì. */
export type DropState = "idle" | "valid" | "invalid";

interface Props {
  column: BoardColumnDef;
  dropState: DropState;
  /** Bộ lọc chung của Board, không gồm trạng thái (cột tự đặt). */
  filter: TicketFilter;
  actionsOf: (ticket: TicketView) => readonly BoardAction[];
  onOpen: (ticket: TicketView) => void;
  onAction: (ticket: TicketView, action: BoardAction) => void;
}

const stateOf = (column: BoardColumnDef, filter: TicketFilter) =>
  filter.state?.length
    ? column.states.filter((s) => filter.state?.includes(s))
    : [...column.states];

/** Một cột của Board: tự tải ticket của mình, cuộn dọc riêng, "Xem thêm" khi còn. */
export const BoardColumn = ({ column, dropState, filter, actionsOf, onOpen, onAction }: Props) => {
  const { setNodeRef, isOver } = useDroppable({ id: column.key });
  const [collapsed, setCollapsed] = useState(column.collapsedByDefault ?? false);
  const [limit, setLimit] = useState(TICKETS_PAGE_SIZE);
  const tickets = useTicketList(
    0,
    // Bộ lọc trạng thái chỉ thu hẹp trong các trạng thái của cột
    { ...filter, state: stateOf(column, filter) },
    {
      size: limit,
      enabled: !collapsed,
    },
  );
  // Ticket Mở lại lên đầu cột (sort ổn định, giữ thứ tự cũ trong mỗi nhóm)
  const items = [...(tickets.data?.items ?? [])].sort(
    (a, b) => Number(b.state === "REOPENED") - Number(a.state === "REOPENED"),
  );
  const names = usePeopleNames(items.map((ticket) => ticket.assignee));
  const total = tickets.data?.total;

  const header = (
    <>
      <span className={cn("size-2 shrink-0 rounded-full", DOT_CLASS[column.tone])} aria-hidden />
      <span className="truncate text-crm-xs font-semibold tracking-wide uppercase">
        {column.label}
      </span>
      {total !== undefined && (
        <span className="text-crm-xs text-crm-text-3 tabular-nums">{total}</span>
      )}
    </>
  );

  // Cột nhận thả: viền nét đứt, liền nét khi con trỏ đang ở trên; cột không nhận: mờ đi
  const dropClass = cn(
    "transition-opacity duration-100 motion-reduce:transition-none",
    dropState === "valid" &&
      cn(
        "outline-2 -outline-offset-2 outline-crm-accent",
        isOver ? "outline-solid" : "outline-dashed",
      ),
    dropState === "invalid" && "opacity-50",
  );

  if (collapsed) {
    return (
      <section
        ref={setNodeRef}
        aria-label={column.label}
        className={cn("w-48 shrink-0 rounded-crm-sm bg-crm-sidebar p-2", dropClass)}
      >
        <button
          type="button"
          onClick={() => setCollapsed(false)}
          className="flex w-full items-center gap-1.5 rounded-crm-sm px-1 py-1 text-left outline-none hover:bg-crm-hover focus-visible:bg-crm-hover"
          aria-expanded={false}
        >
          <IconChevronRight size={14} stroke={2} className="shrink-0 text-crm-text-3" />
          {header}
        </button>
      </section>
    );
  }

  return (
    <section
      ref={setNodeRef}
      aria-label={column.label}
      className={cn(
        "flex max-h-full w-72 shrink-0 flex-col rounded-crm-sm bg-crm-sidebar p-2 md:w-72",
        dropClass,
      )}
    >
      <h2 className="mb-2 flex items-center gap-1.5 px-1">{header}</h2>

      <div className="flex min-h-0 flex-1 flex-col gap-2 overflow-y-auto">
        {tickets.isPending && (
          <>
            <Skeleton className="h-20 w-full" />
            <Skeleton className="h-20 w-full" />
          </>
        )}
        {tickets.error && <ErrorState error={tickets.error} fallback="Không tải được cột này." />}
        {tickets.data && items.length === 0 && (
          <p className="px-1 py-2 text-crm-sm text-crm-text-3">Không có ticket</p>
        )}
        {items.map((ticket) => (
          <TicketCard
            key={ticket.id}
            ticket={ticket}
            names={names}
            actions={actionsOf(ticket)}
            onOpen={onOpen}
            onAction={onAction}
          />
        ))}
        {total !== undefined && items.length < total && (
          <Button
            variant="ghost"
            size="sm"
            disabled={tickets.isFetching}
            onClick={() => setLimit((current) => current + TICKETS_PAGE_SIZE)}
          >
            {tickets.isFetching ? "Đang tải..." : `Xem thêm (${total - items.length})`}
          </Button>
        )}
      </div>

      {column.collapsedByDefault && (
        <button
          type="button"
          onClick={() => setCollapsed(true)}
          className="mt-2 rounded-crm-sm px-1 py-1 text-left text-crm-sm text-crm-text-3 outline-none hover:bg-crm-hover"
        >
          Thu gọn cột
        </button>
      )}
    </section>
  );
};
