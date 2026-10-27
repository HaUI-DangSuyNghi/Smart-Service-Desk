import { useState } from "react";
import { IconCircleCheck } from "@tabler/icons-react";
import { Button } from "../../../platform/shared/ui/button";
import { FormField } from "../../../platform/shared/ui/FormField";
import { Textarea } from "../../../platform/shared/ui/textarea";
import { toast, toastError } from "../../../platform/shared/ui/toast";
import { MAX_REASON } from "./ticketRules";
import { useConfirmResolution } from "./useTickets";

interface Props {
  taskId: string;
}

/**
 * Người gửi xác nhận kết quả (R11): "Đã ổn" đóng ticket, "Chưa xong" mở lại cho kỹ thuật viên
 * và bắt buộc ghi còn lỗi gì.
 */
export const ConfirmResolution = ({ taskId }: Props) => {
  const confirm = useConfirmResolution();
  const [isReworking, setReworking] = useState(false);
  const [reason, setReason] = useState("");
  const trimmed = reason.trim();

  const handleConfirm = (outcome: "accepted" | "rework") => {
    confirm.mutate(
      { taskId, outcome, comment: outcome === "rework" ? trimmed : undefined },
      {
        onSuccess: () =>
          outcome === "accepted"
            ? toast.success("Đã đóng yêu cầu", { description: "Cảm ơn bạn đã xác nhận." })
            : toast.success("Đã báo chưa xong", {
                description: "Kỹ thuật viên sẽ được báo để xử lý tiếp.",
              }),
        onError: (error) => toastError("Không gửi được xác nhận.", error),
      },
    );
  };

  return (
    <section className="border-b border-crm-line bg-crm-accent-soft px-4 py-4">
      <h2 className="mb-1 flex items-center gap-1.5 font-medium">
        <IconCircleCheck size={16} stroke={2} className="text-crm-accent" />
        Kỹ thuật viên đã báo xử lý xong
      </h2>
      <p className="mb-3 text-crm-text-2">Bạn kiểm tra lại giúp: sự cố đã được khắc phục chưa?</p>

      {isReworking ? (
        <div className="flex flex-col gap-2">
          <FormField id="rework-reason" label="Còn lỗi gì?">
            <Textarea
              id="rework-reason"
              maxLength={MAX_REASON}
              value={reason}
              onChange={(event) => setReason(event.target.value)}
            />
          </FormField>
          <div className="flex flex-wrap gap-2">
            <Button
              size="sm"
              disabled={confirm.isPending || trimmed.length === 0}
              onClick={() => handleConfirm("rework")}
            >
              {confirm.isPending ? "Đang gửi..." : "Gửi, mở lại yêu cầu"}
            </Button>
            <Button size="sm" variant="outline" onClick={() => setReworking(false)}>
              Hủy
            </Button>
          </div>
        </div>
      ) : (
        <div className="flex flex-wrap gap-2">
          <Button size="sm" disabled={confirm.isPending} onClick={() => handleConfirm("accepted")}>
            {confirm.isPending ? "Đang gửi..." : "Đã ổn"}
          </Button>
          <Button
            size="sm"
            variant="outline"
            disabled={confirm.isPending}
            onClick={() => setReworking(true)}
          >
            Chưa xong
          </Button>
        </div>
      )}
    </section>
  );
};
