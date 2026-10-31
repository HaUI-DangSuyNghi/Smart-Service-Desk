import { useState, type SyntheticEvent } from "react";
import { Button } from "../../../platform/shared/ui/button";
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
} from "../../../platform/shared/ui/dialog";
import { ErrorState } from "../../../platform/shared/ui/ErrorState";
import { TextSkeleton } from "../../../platform/shared/ui/Skeleton";
import { ConfirmResolutionForm } from "./ConfirmResolutionForm";
import { EnrichmentPanel } from "./EnrichmentPanel";
import { useIntake } from "./useIntake";
import { useTicket } from "./useTickets";

interface Props {
  workspace: string;
  ticketId: string;
  /** Ticket đang ở bước người gửi xác nhận kết quả. */
  awaitingConfirmation: boolean;
  /** Việc xác nhận đang mở nếu nơi gọi đã có; không có thì tải ticket khi mở dialog. */
  taskId?: string | null;
}

const ConfirmContent = ({
  workspace,
  ticketId,
  taskId,
  onDone,
}: Props & { onDone: () => void }) => {
  // Tải ticket để lấy tiêu đề (và việc xác nhận khi nơi gọi chưa có)
  const ticket = useTicket(workspace, ticketId);
  const id = taskId ?? ticket.data?.taskId;
  if (id) return <ConfirmResolutionForm taskId={id} title={ticket.data?.title} onDone={onDone} />;
  return (
    <>
      <DialogHeader>
        <DialogTitle>Xác nhận kết quả</DialogTitle>
      </DialogHeader>
      {ticket.error ? (
        <ErrorState error={ticket.error} fallback="Không tải được ticket." />
      ) : (
        <TextSkeleton lines={2} label="Đang tải" />
      )}
    </>
  );
};

const EnrichmentContent = ({ workspace, ticketId }: Pick<Props, "workspace" | "ticketId">) => {
  const intake = useIntake(workspace, ticketId);
  if (intake.isPending) return <TextSkeleton lines={3} label="Đang tải" />;
  if (intake.error) {
    return <ErrorState error={intake.error} fallback="Chưa tải được trạng thái tiếp nhận." />;
  }
  return (
    <EnrichmentPanel
      workspace={workspace}
      ticketId={ticketId}
      managedIntake={!!intake.data}
      embedded
    />
  );
};

/**
 * Nút thao tác của người gửi trên dòng danh sách: Chờ xác nhận thì "Xác nhận" (Đã ổn / Chưa xong),
 * các trạng thái trước đó thì "Bổ sung thông tin". Dữ liệu chỉ tải khi mở dialog.
 */
export const RequesterTicketAction = (props: Props) => {
  const [open, setOpen] = useState(false);
  // Dòng bảng bấm được để mở chi tiết; dialog nằm trong portal nhưng sự kiện vẫn lan theo cây React
  const stop = (event: SyntheticEvent) => event.stopPropagation();
  const confirming = props.awaitingConfirmation;

  return (
    <div onClick={stop} onKeyDown={stop}>
      <Button
        size="xs"
        variant={confirming ? "default" : "outline"}
        className="w-full"
        onClick={() => setOpen(true)}
      >
        {confirming ? "Xác nhận" : "Bổ sung thông tin"}
      </Button>
      <Dialog open={open} onOpenChange={setOpen}>
        <DialogContent
          className={confirming ? "sm:max-w-100" : "sm:max-w-130"}
          aria-describedby={undefined}
        >
          {open &&
            (confirming ? (
              <ConfirmContent {...props} onDone={() => setOpen(false)} />
            ) : (
              <>
                <DialogHeader>
                  <DialogTitle>Bổ sung thông tin</DialogTitle>
                </DialogHeader>
                <EnrichmentContent workspace={props.workspace} ticketId={props.ticketId} />
              </>
            ))}
        </DialogContent>
      </Dialog>
    </div>
  );
};
