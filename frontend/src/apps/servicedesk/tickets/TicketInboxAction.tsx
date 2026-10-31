import { useState } from "react";
import { useCurrentWorkspace } from "../../../platform/human/workspace";
import type { InboxActionProps } from "../../../platform/process";
import { Button } from "../../../platform/shared/ui/button";
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
} from "../../../platform/shared/ui/dialog";
import { ErrorState } from "../../../platform/shared/ui/ErrorState";
import { FieldsSkeleton } from "../../../platform/shared/ui/Skeleton";
import { ReleaseIntakeDialog } from "./ReleaseIntakeDialog";
import { ReviewDialog } from "./ReviewDialog";
import { useTicket } from "./useTickets";

const ReviewContent = ({
  task,
  workspace,
  onClose,
}: Pick<InboxActionProps, "task"> & { workspace: string; onClose: () => void }) => {
  const ticket = useTicket(workspace, task.businessKey);
  if (ticket.data) return <ReviewDialog ticket={ticket.data} onClose={onClose} />;
  return (
    <Dialog
      open
      onOpenChange={(open) => {
        if (!open) onClose();
      }}
    >
      <DialogContent aria-describedby={undefined}>
        <DialogHeader>
          <DialogTitle>Duyệt và giao việc</DialogTitle>
        </DialogHeader>
        {ticket.error ? (
          <ErrorState error={ticket.error} />
        ) : (
          <FieldsSkeleton rows={3} label="Đang tải ticket" />
        )}
      </DialogContent>
    </Dialog>
  );
};

/** Service Desk owns intake and approval; shared inbox keeps generic assignment for other work. */
export const TicketInboxAction = ({ task, fallback }: InboxActionProps) => {
  const [open, setOpen] = useState(false);
  const { current } = useCurrentWorkspace();
  if (!["intake", "review"].includes(task.key)) return fallback;
  if (!current) return null;
  const intake = task.key === "intake";
  return (
    <span
      className="block w-full"
      onClick={(event) => event.stopPropagation()}
      onKeyDown={(event) => event.stopPropagation()}
    >
      <Button
        size="sm"
        variant={intake ? "warning" : "success"}
        className="min-h-9 w-full md:min-h-7"
        onClick={() => setOpen(true)}
      >
        {intake ? "Tiếp nhận" : "Duyệt và giao việc"}
      </Button>
      {intake ? (
        <ReleaseIntakeDialog
          target={open ? { workspace: current, id: task.businessKey } : null}
          onClose={() => setOpen(false)}
        />
      ) : (
        open && <ReviewContent task={task} workspace={current} onClose={() => setOpen(false)} />
      )}
    </span>
  );
};
