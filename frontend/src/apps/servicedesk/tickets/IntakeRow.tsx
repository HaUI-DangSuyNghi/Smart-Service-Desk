import type { MouseEvent } from "react";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { IconDots } from "@tabler/icons-react";
import { useActor } from "../../../platform/human/workspace";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from "../../../platform/shared/ui/dropdown-menu";
import { Skeleton } from "../../../platform/shared/ui/Skeleton";
import { Tag } from "../../../platform/shared/ui/Tag";
import { toast, toastError } from "../../../platform/shared/ui/toast";
import { evaluateIntake } from "../services/intakeService";
import type { TicketView } from "../services/ticketService";
import { INTAKE_STATUS_LABELS } from "./ticketLabels";
import { canLinkCanonical, canReleaseIntake } from "./ticketRules";
import { useIntake } from "./useIntake";

export type IntakeAction = "release" | "canonical" | "split";

const ACTION_LABELS: Readonly<Record<IntakeAction, string>> = {
  release: "Tiếp nhận",
  canonical: "Kiểm tra ticket trùng",
  split: "Tách khỏi ticket gốc",
};

/** Trạng thái tiếp nhận của một dòng (cùng query với menu nên chỉ gọi API một lần). */
export const IntakeStatusCell = ({ ticket }: { ticket: TicketView }) => {
  const intake = useIntake(ticket.workspace, ticket.id);
  if (intake.isPending) return <Skeleton className="h-4 w-32" />;
  if (!intake.data) return <span className="text-crm-text-3">—</span>;
  return (
    <span className="flex flex-wrap items-center gap-1">
      <Tag tone={intake.data.status === "MANUAL_REQUIRED" ? "orange" : "gray"}>
        {INTAKE_STATUS_LABELS[intake.data.status]}
      </Tag>
      {intake.data.lateNotice && <Tag tone="orange">Có thông tin mới</Tag>}
    </span>
  );
};

/** Menu ⋯ trên dòng: tiếp nhận, đánh giá lại, gộp / tách ticket trùng. */
export const IntakeRowMenu = ({
  ticket,
  onAction,
}: {
  ticket: TicketView;
  onAction: (ticket: TicketView, action: IntakeAction) => void;
}) => {
  const cache = useQueryClient();
  const { actor, roles } = useActor();
  const intake = useIntake(ticket.workspace, ticket.id);
  const evaluate = useMutation({
    mutationFn: () => evaluateIntake(ticket.workspace, ticket.id),
    onSuccess: () => toast.success("Đã đánh giá lại"),
    onError: (error) => toastError("Chưa đánh giá lại được.", error),
    onSettled: () => cache.invalidateQueries({ queryKey: ["servicedesk"] }),
  });
  // Dòng bấm được để mở chi tiết: chặn cú bấm lan lên dòng
  const stop = (event: MouseEvent) => event.stopPropagation();

  const state = intake.data;
  if (!state) return null;
  // Người gửi không tự tiếp nhận / gộp ticket của mình (backend chặn)
  const coordinator = roles.includes("coordinator") && actor?.subject !== ticket.creator;
  const following = state.status === "CANONICAL_FOLLOWER";
  const actions: IntakeAction[] = [];
  if (coordinator && canReleaseIntake(state)) actions.push("release");
  if (coordinator && canLinkCanonical(state)) actions.push(following ? "split" : "canonical");
  const canEvaluate = canReleaseIntake(state);
  if (actions.length === 0 && !canEvaluate) return null;

  return (
    <DropdownMenu>
      <DropdownMenuTrigger
        aria-label="Thao tác tiếp nhận"
        onClick={stop}
        className="flex size-7 items-center justify-center rounded-crm-sm text-crm-text-3 outline-none hover:bg-crm-press focus-visible:bg-crm-press data-[state=open]:bg-crm-press"
      >
        <IconDots size={16} stroke={2} />
      </DropdownMenuTrigger>
      <DropdownMenuContent align="end" className="w-auto min-w-44" onClick={stop}>
        {actions.map((action) => (
          <DropdownMenuItem key={action} onSelect={() => onAction(ticket, action)}>
            {ACTION_LABELS[action]}
          </DropdownMenuItem>
        ))}
        {canEvaluate && (
          <DropdownMenuItem disabled={evaluate.isPending} onSelect={() => evaluate.mutate()}>
            Đánh giá lại
          </DropdownMenuItem>
        )}
      </DropdownMenuContent>
    </DropdownMenu>
  );
};
