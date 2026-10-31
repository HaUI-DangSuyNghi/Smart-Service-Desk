import type { KeyboardEvent, ReactNode } from "react";
import { useDraggable } from "@dnd-kit/core";
import { IconAlertTriangle, IconRepeat, IconUser } from "@tabler/icons-react";
import { cn } from "cn";
import { personName } from "../../../../platform/human/workspace";
import { AssigneeMenu, PriorityTag, SlaIndicator } from "../../../../platform/process";
import { Avatar } from "../../../../platform/shared/ui/Avatar";
import { Tag, type TagTone } from "../../../../platform/shared/ui/Tag";
import { Tooltip } from "../../../../platform/shared/ui/Tooltip";
import type { TicketView } from "../../services/ticketService";
import { ticketCode } from "../ticketLabels";
import { aiSuggestion, isStarted } from "../ticketRules";
import { useRefreshTickets } from "../useTickets";
import { CardMenu } from "./CardMenu";
import type { BoardAction } from "./boardAccess";

const UNASSIGNED_CLASS =
  "inline-flex size-6 shrink-0 items-center justify-center rounded-full border border-dashed border-crm-line text-crm-text-3 outline-none focus-visible:ring-2 focus-visible:ring-crm-focus";

const CARD_CLASS =
  "flex h-40 flex-col gap-2 rounded-crm-sm border border-crm-line bg-crm-surface p-3 outline-none";

const Chip = ({
  tone,
  title,
  children,
}: {
  tone: TagTone;
  title?: string;
  children: ReactNode;
}) => (
  <span title={title}>
    <Tag tone={tone}>
      <span className="inline-flex items-center gap-1">{children}</span>
    </Tag>
  </span>
);

interface ContentProps {
  ticket: TicketView;
  names: ReadonlyMap<string, string>;
  /** Mục sửa trên menu; rỗng thì không có menu. */
  actions: readonly BoardAction[];
  onAction: (ticket: TicketView, action: BoardAction) => void;
}

/**
 * Nội dung thẻ, từ trên xuống: mã (+ menu ⋯), tiêu đề tối đa 2 dòng, hạn xử lý (luôn giữ chỗ), rồi
 * hàng nhãn ưu tiên và cảnh báo với người xử lý bên phải. Dùng cho cả thẻ trong cột và bản sao
 * bay theo con trỏ khi kéo.
 */
const CardContent = ({ ticket, names, actions, onAction }: ContentProps) => {
  const refresh = useRefreshTickets();
  // Chưa duyệt thì hiện gợi ý AI để điều phối viên sắp xếp
  const suggestion = aiSuggestion(ticket.assessment);
  const priority = ticket.classification?.priority ?? suggestion?.priority;
  const category = ticket.classification?.category ?? suggestion?.category;
  const assignee = ticket.assignee ? personName(names, ticket.assignee) : null;
  const reopenCount = ticket.reopenCount ?? 0;
  // AI không phân loại được: điều phối viên phải chọn tay
  const needsManualReview =
    (ticket.state === "AWAITING_REVIEW" || ticket.state === "UNCLASSIFIED") && !category;
  // Bấm avatar: giao lại (đang xử lý) hoặc duyệt và giao việc (chưa giao); chỉ ai có menu sửa mới có
  const avatarAction: BoardAction | null = actions.includes("reassign")
    ? "reassign"
    : !assignee && actions.includes("review")
      ? "review"
      : null;
  const avatarHint = avatarAction === "reassign" ? "Bấm để giao lại" : "Bấm để duyệt và giao việc";

  return (
    <>
      <div className="flex h-5 items-center justify-between gap-1">
        <span className="font-medium text-crm-text-3 tabular-nums">{ticketCode(ticket.id)}</span>
        {actions.length > 0 && <CardMenu ticket={ticket} actions={actions} onAction={onAction} />}
      </div>

      {/* Tối đa 2 dòng, dài hơn thì cắt bằng "…" */}
      <p className="line-clamp-2 min-h-[3em] font-medium wrap-break-word">{ticket.title}</p>

      {/* Hạn xử lý: luôn giữ chỗ một hàng, không có hạn thì trống để các hàng dưới không bị đẩy lên */}
      <div className="flex min-h-5 items-center">
        {ticket.sla && <SlaIndicator chip sla={ticket.sla} started={isStarted(ticket)} />}
      </div>

      <div className="mt-auto flex min-h-6 items-center justify-between gap-2">
        <span className="flex min-w-0 flex-wrap items-center gap-1 overflow-hidden">
          {priority && (
            <PriorityTag priority={priority} escalated={ticket.classification?.escalated} />
          )}
          {reopenCount > 0 && (
            <Chip tone="red" title={`Mở lại ${reopenCount} lần`}>
              <IconRepeat size={12} stroke={2} />
              Mở lại {reopenCount}
            </Chip>
          )}
          {needsManualReview && (
            <Chip tone="orange" title="AI không phân loại được, cần chọn thủ công">
              <IconAlertTriangle size={12} stroke={2} />
              Chưa phân loại
            </Chip>
          )}
        </span>
        {avatarAction === "reassign" && ticket.taskId ? (
          // Giao lại: chọn người ngay trong dropdown, không mở dialog
          <AssigneeMenu
            taskId={ticket.taskId}
            assignee={ticket.assignee ?? null}
            name={assignee}
            onAssigned={() => void refresh()}
          />
        ) : assignee ? (
          <Avatar
            name={assignee}
            hint={avatarAction ? avatarHint : undefined}
            onClick={avatarAction ? () => onAction(ticket, avatarAction) : undefined}
          />
        ) : (
          <Tooltip content={avatarAction ? `Chưa giao · ${avatarHint}` : "Chưa giao"}>
            {avatarAction ? (
              <button
                type="button"
                aria-label={`Chưa giao, ${avatarHint.toLowerCase()}`}
                onClick={(event) => {
                  event.stopPropagation();
                  onAction(ticket, avatarAction);
                }}
                className={`${UNASSIGNED_CLASS} cursor-pointer hover:bg-crm-press`}
              >
                <IconUser size={14} stroke={2} />
              </button>
            ) : (
              // Không phải nút để tooltip vẫn hiện (nút bị disabled không nhận hover)
              <span role="img" aria-label="Chưa giao" tabIndex={0} className={UNASSIGNED_CLASS}>
                <IconUser size={14} stroke={2} />
              </span>
            )}
          </Tooltip>
        )}
      </div>
    </>
  );
};

interface Props extends ContentProps {
  onOpen: (ticket: TicketView) => void;
}

/**
 * Thẻ ticket trong cột: bấm mở chi tiết, kéo (chuột, cảm ứng giữ ~0,2 giây, hoặc phím Space) sang
 * cột được viền để thao tác. Khi đang kéo, thẻ gốc mờ đi và bản sao bay theo con trỏ.
 * Ai cũng nhấc được thẻ; thẻ không có thao tác thì mọi cột khác mờ đi, thả ra là bay về chỗ cũ (toast giải thích).
 */
export const TicketCard = ({ ticket, names, actions, onOpen, onAction }: Props) => {
  const { attributes, listeners, setNodeRef, isDragging } = useDraggable({
    id: ticket.id,
    data: { ticket },
  });

  const handleKeyDown = (event: KeyboardEvent<HTMLDivElement>) => {
    // Space dành cho nhấc thẻ (KeyboardSensor); Enter mở chi tiết
    if (event.target === event.currentTarget && event.key === "Enter") {
      event.preventDefault();
      onOpen(ticket);
    }
  };

  return (
    <div
      ref={setNodeRef}
      // attributes của dnd-kit đã có role, tabIndex và mô tả cho trình đọc màn hình
      {...attributes}
      {...listeners}
      aria-label={`${ticket.title}, mở chi tiết`}
      onClick={() => onOpen(ticket)}
      onKeyDown={(event) => {
        listeners?.onKeyDown?.(event);
        handleKeyDown(event);
      }}
      className={cn(
        CARD_CLASS,
        "hover:bg-crm-hover focus-visible:border-crm-focus",
        "cursor-grab touch-manipulation active:cursor-grabbing",
        isDragging && "opacity-40",
      )}
    >
      <CardContent ticket={ticket} names={names} actions={actions} onAction={onAction} />
    </div>
  );
};

/** Bản sao thẻ bay theo con trỏ khi đang kéo (DragOverlay). */
export const TicketCardOverlay = ({ ticket, names }: Pick<ContentProps, "ticket" | "names">) => (
  <div
    className={cn(
      CARD_CLASS,
      "w-72 cursor-grabbing rotate-2 shadow-lg ring-1 ring-crm-line motion-reduce:rotate-0",
    )}
  >
    <CardContent ticket={ticket} names={names} actions={[]} onAction={() => undefined} />
  </div>
);
