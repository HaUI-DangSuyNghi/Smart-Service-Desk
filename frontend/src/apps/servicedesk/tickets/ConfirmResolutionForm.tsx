import { useState } from "react";
import { IconCircleCheck } from "@tabler/icons-react";
import { Button } from "../../../platform/shared/ui/button";
import {
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "../../../platform/shared/ui/dialog";
import { FormField } from "../../../platform/shared/ui/FormField";
import { Textarea } from "../../../platform/shared/ui/textarea";
import { toast, toastError } from "../../../platform/shared/ui/toast";
import { MAX_REASON } from "./ticketRules";
import { useConfirmResolution } from "./useTickets";

interface Props {
  taskId: string;
  /** Tiêu đề ticket, hiện dưới tiêu đề dialog. */
  title?: string;
  onDone: () => void;
}

/**
 * Người gửi xác nhận kết quả trong dialog (R11), cùng bố cục các form khác: "Đã ổn" đóng ticket,
 * "Chưa xong" mở ô ghi còn lỗi gì rồi mở lại cho kỹ thuật viên.
 */
export const ConfirmResolutionForm = ({ taskId, title, onDone }: Props) => {
  const confirm = useConfirmResolution();
  const [isReworking, setReworking] = useState(false);
  const [reason, setReason] = useState("");
  const trimmed = reason.trim();

  const handleConfirm = (outcome: "accepted" | "rework") => {
    confirm.mutate(
      { taskId, outcome, comment: outcome === "rework" ? trimmed : undefined },
      {
        onSuccess: () => {
          toast.success(outcome === "accepted" ? "Đã đóng yêu cầu" : "Đã mở lại yêu cầu");
          onDone();
        },
        onError: (error) => toastError("Không gửi được xác nhận.", error),
      },
    );
  };

  return (
    <>
      <DialogHeader>
        <DialogTitle>Xác nhận kết quả</DialogTitle>
        {title && <DialogDescription className="truncate">{title}</DialogDescription>}
      </DialogHeader>

      {/* Khung nền nhạt + icon làm điểm nhấn; hai dòng cách đều nhau */}
      {!isReworking && (
        <div className="flex items-start gap-2.5 rounded-crm-sm bg-crm-accent-soft p-3">
          <IconCircleCheck size={18} stroke={2} className="mt-px shrink-0 text-crm-accent" />
          <div className="flex flex-col gap-1 leading-relaxed">
            <p className="font-medium text-crm-text">Kỹ thuật viên đã báo xử lý xong</p>
            <p className="text-crm-text-2">Bạn kiểm tra giúp: sự cố đã được khắc phục chưa?</p>
          </div>
        </div>
      )}

      {isReworking && (
        <FormField id="rework-reason" label="Còn lỗi gì?">
          <Textarea
            id="rework-reason"
            maxLength={MAX_REASON}
            value={reason}
            onChange={(event) => setReason(event.target.value)}
          />
        </FormField>
      )}

      <DialogFooter>
        {isReworking ? (
          <>
            <Button variant="outline" size="sm" onClick={() => setReworking(false)}>
              Quay lại
            </Button>
            <Button
              size="sm"
              disabled={confirm.isPending || trimmed.length === 0}
              onClick={() => handleConfirm("rework")}
            >
              {confirm.isPending ? "Đang gửi..." : "Gửi"}
            </Button>
          </>
        ) : (
          <>
            <Button
              variant="outline"
              size="sm"
              disabled={confirm.isPending}
              onClick={() => setReworking(true)}
            >
              Chưa xong
            </Button>
            <Button
              size="sm"
              disabled={confirm.isPending}
              onClick={() => handleConfirm("accepted")}
            >
              {confirm.isPending ? "Đang gửi..." : "Đã ổn"}
            </Button>
          </>
        )}
      </DialogFooter>
    </>
  );
};
