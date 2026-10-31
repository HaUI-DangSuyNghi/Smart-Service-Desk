import { useState } from "react";
import { useNavigate } from "react-router";
import {
  DndContext,
  DragOverlay,
  KeyboardSensor,
  PointerSensor,
  TouchSensor,
  pointerWithin,
  useSensor,
  useSensors,
  type Announcements,
  type DragEndEvent,
  type DragStartEvent,
} from "@dnd-kit/core";
import { usePeopleNames } from "../../../../platform/human/workspace";
import {
  AssignTaskDialog,
  CompleteTaskDialog,
  PauseDialog,
  ReleaseDialog,
  useQueue,
  useResumeWork,
  useStartWork,
  useTimeline,
  workErrorMessage,
} from "../../../../platform/process";
import type { QueueItem } from "../../../../platform/services";
import { toast, toastError } from "../../../../platform/shared/ui/toast";
import type { TicketView } from "../../services/ticketService";
import { CorrectClassificationDialog } from "../CorrectClassificationDialog";
import { ReleaseIntakeDialog } from "../ReleaseIntakeDialog";
import type { Viewer } from "../ticketPermissions";
import { ReviewDialog } from "../ReviewDialog";
import { useRefreshTickets, type TicketFilter } from "../useTickets";
import { boardActions, type BoardAction } from "./boardAccess";
import { BoardColumn, type DropState } from "./BoardColumn";
import { BOARD_COLUMNS, columnOf } from "./boardColumns";
import { dropRejection, dropTargets, type DropAction } from "./dropTargets";
import { TicketCardOverlay } from "./TicketCard";

/** Giao lại: lấy việc đang mở của ticket từ tiến độ rồi mở dialog giao việc của khối P. */
const ReassignDialog = ({ ticket, onClose }: { ticket: TicketView; onClose: () => void }) => {
  const timeline = useTimeline(ticket.process.processInstanceId);
  const task = timeline.data?.currentTasks.find((item) => item.id === ticket.taskId) ?? null;
  return <AssignTaskDialog task={task} onClose={onClose} />;
};

type WorkDialog = { kind: "pause" | "complete" | "release"; item: QueueItem };

const titleOf = (data: { current?: Record<string, unknown> } | null | undefined) =>
  (data?.current?.ticket as TicketView | undefined)?.title ?? "ticket";

const columnLabel = (key: string) =>
  BOARD_COLUMNS.find((column) => column.key === key)?.label ?? key;

// Thông báo cho trình đọc màn hình khi kéo bằng bàn phím
const ANNOUNCEMENTS: Announcements = {
  onDragStart: ({ active }) => `Đã nhấc ticket ${titleOf(active.data)}.`,
  onDragOver: ({ active, over }) =>
    over
      ? `Ticket ${titleOf(active.data)} đang ở trên cột ${columnLabel(String(over.id))}.`
      : undefined,
  onDragEnd: ({ active }) => `Đã thả ticket ${titleOf(active.data)}.`,
  onDragCancel: ({ active }) => `Đã hủy kéo ticket ${titleOf(active.data)}.`,
};

interface Props {
  /** Bộ lọc chung (ưu tiên, quá hạn, giao cho tôi); trạng thái do từng cột đặt. */
  filter: TicketFilter;
  viewer: Viewer;
}

/**
 * Board kiểu Jira: một cột cho mỗi nhóm trạng thái R6. Chung cho điều phối viên và kỹ thuật viên
 * (cùng tập ticket). Kéo thẻ sang cột được viền để thao tác (chỉ tác động vào trạng thái mình sửa
 * được); thả sai chỗ thì báo bằng toast. Menu sửa trên thẻ chỉ có ở điều phối viên, siêu quản trị.
 */
export const TicketBoard = ({ filter, viewer }: Props) => {
  const navigate = useNavigate();
  const refresh = useRefreshTickets();
  const start = useStartWork();
  const resume = useResumeWork();
  // Việc của chính mình: dialog tạm dừng, báo xong, trả việc cần bản ghi này
  const queue = useQueue(undefined, viewer.canHandle);
  const [editing, setEditing] = useState<{ ticket: TicketView; action: BoardAction } | null>(null);
  const [workDialog, setWorkDialog] = useState<WorkDialog | null>(null);
  const [active, setActive] = useState<TicketView | null>(null);
  const names = usePeopleNames([active?.assignee]);

  const sensors = useSensors(
    // Phải rê xa một chút mới tính là kéo, nên bấm thẻ vẫn mở chi tiết
    useSensor(PointerSensor, { activationConstraint: { distance: 6 } }),
    // Cảm ứng: giữ ~0,2 giây mới kéo, để vuốt ngang vẫn cuộn được Board
    useSensor(TouchSensor, { activationConstraint: { delay: 200, tolerance: 8 } }),
    // Space nhấc/thả thẻ; Enter vẫn mở chi tiết
    useSensor(KeyboardSensor, {
      keyboardCodes: { start: ["Space"], cancel: ["Escape"], end: ["Space"] },
    }),
  );

  const closeEditing = () => {
    setEditing(null);
    void refresh();
  };
  const closeWorkDialog = () => {
    setWorkDialog(null);
    void refresh();
  };
  const open = (action: BoardAction) => (editing?.action === action ? editing.ticket : null);
  const dialogItem = (kind: WorkDialog["kind"]) =>
    workDialog?.kind === kind ? workDialog.item : null;

  const run = (action: DropAction, ticket: TicketView) => {
    if (action === "intake" || action === "review") {
      setEditing({ ticket, action });
      return;
    }
    const item = queue.data?.items.find((entry) => entry.id === ticket.taskId);
    if (!item) {
      toast.info("Chưa tải xong việc của bạn, thử lại sau giây lát.");
      return;
    }
    if (action === "start" || action === "resume") {
      const mutation = action === "start" ? start : resume;
      mutation.mutate(item.id, {
        onSuccess: () => {
          toast.success(action === "start" ? "Đã bắt đầu" : "Đã tiếp tục", {
            description: item.label,
          });
          void refresh();
        },
        onError: (error) =>
          toastError(
            workErrorMessage(
              error,
              action === "start" ? "Không bắt đầu được." : "Không tiếp tục được.",
            ),
            error,
          ),
      });
      return;
    }
    setWorkDialog({ kind: action, item });
  };

  const handleDragStart = ({ active: dragged }: DragStartEvent) =>
    setActive((dragged.data.current?.ticket as TicketView | undefined) ?? null);

  const handleDragEnd = ({ active: dragged, over }: DragEndEvent) => {
    setActive(null);
    const ticket = dragged.data.current?.ticket as TicketView | undefined;
    // Thả ra ngoài mọi cột, hoặc về lại cột cũ: không có gì xảy ra
    if (!ticket || !over) return;
    const target = String(over.id);
    if (columnOf(ticket.state)?.key === target) return;

    const action = dropTargets(ticket, viewer)[target];
    if (action) run(action, ticket);
    else toast.info(dropRejection(ticket, viewer));
  };

  // Lọc theo trạng thái thì chỉ hiện cột có trạng thái đó
  const visibleColumns = BOARD_COLUMNS.filter(
    (column) => !filter.state?.length || column.states.some((s) => filter.state?.includes(s)),
  );

  const targets = active ? dropTargets(active, viewer) : null;
  const dropStateOf = (columnKey: string): DropState => {
    if (!active || !targets) return "idle";
    if (targets[columnKey]) return "valid";
    // Cột gốc không mờ đi: thẻ vẫn nằm đó
    return columnOf(active.state)?.key === columnKey ? "idle" : "invalid";
  };

  return (
    <DndContext
      sensors={sensors}
      collisionDetection={pointerWithin}
      accessibility={{
        announcements: ANNOUNCEMENTS,
        screenReaderInstructions: {
          draggable:
            "Nhấn Space để nhấc ticket, phím mũi tên để chọn cột, Space để thả, Escape để hủy. Nhấn Enter để mở chi tiết.",
        },
      }}
      onDragStart={handleDragStart}
      onDragEnd={handleDragEnd}
      onDragCancel={() => setActive(null)}
    >
      <div className="flex h-full items-start gap-2 overflow-x-auto p-3">
        {visibleColumns.map((column) => (
          <BoardColumn
            key={column.key}
            column={column}
            dropState={dropStateOf(column.key)}
            filter={filter}
            actionsOf={(ticket) => boardActions(ticket, viewer)}
            onOpen={(ticket) => navigate(`/servicedesk/tickets/${ticket.id}`)}
            onAction={(ticket, action) => setEditing({ ticket, action })}
          />
        ))}
      </div>

      <DragOverlay dropAnimation={{ duration: 150, easing: "ease-out" }}>
        {active && <TicketCardOverlay ticket={active} names={names} />}
      </DragOverlay>

      <ReleaseIntakeDialog
        target={editing?.action === "intake" ? editing.ticket : null}
        onClose={closeEditing}
      />
      <ReviewDialog ticket={open("review")} onClose={closeEditing} />
      <CorrectClassificationDialog ticket={open("correct")} onClose={closeEditing} />
      {editing?.action === "reassign" && (
        <ReassignDialog key={editing.ticket.id} ticket={editing.ticket} onClose={closeEditing} />
      )}
      <PauseDialog item={dialogItem("pause")} onClose={closeWorkDialog} />
      <CompleteTaskDialog task={dialogItem("complete")} onClose={closeWorkDialog} />
      <ReleaseDialog item={dialogItem("release")} onClose={closeWorkDialog} />
    </DndContext>
  );
};
