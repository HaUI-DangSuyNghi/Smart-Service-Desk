import { useForm, useWatch } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import type { QueueItem } from "@/platform/services";
import { submitOnEnter } from "@/platform/shared/lib/submitOnEnter";
import { Button } from "@/platform/shared/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/platform/shared/ui/dialog";
import { FormField } from "@/platform/shared/ui/FormField";
import { Input } from "@/platform/shared/ui/input";
import { Textarea } from "@/platform/shared/ui/textarea";
import { toast, toastError } from "@/platform/shared/ui/toast";
import { useRequestEstimate } from "./useWork";
import { workErrorMessage } from "./workErrors";
import { estimateBasisText, formatMinutes } from "./workLabels";

const MAX_REASON = 1000;
// Một tuần, khớp maximum của proposedMinutes trong contract
const MAX_MINUTES = 10080;

const schema = z.object({
  proposedMinutes: z.coerce
    .number({ message: "Nhập số phút" })
    .int("Nhập số nguyên")
    .min(1, "Ít nhất 1 phút")
    .max(MAX_MINUTES, `Tối đa ${MAX_MINUTES} phút`),
  reason: z.string().trim().min(1, "Ghi lý do").max(MAX_REASON, `Tối đa ${MAX_REASON} ký tự`),
});

type FormInput = z.input<typeof schema>;
type FormValues = z.output<typeof schema>;

interface FormProps {
  item: QueueItem;
  onDone: () => void;
}

const EstimateRequestForm = ({ item, onDone }: FormProps) => {
  const request = useRequestEstimate();
  const form = useForm<FormInput, unknown, FormValues>({
    resolver: zodResolver(schema),
    defaultValues: { proposedMinutes: item.estimate.minutes, reason: "" },
  });
  const reason = useWatch({ control: form.control, name: "reason" });
  const { errors } = form.formState;

  const handleSubmit = form.handleSubmit(({ proposedMinutes, reason }) => {
    request.mutate(
      { taskId: item.id, proposedMinutes, reason },
      {
        onSuccess: () => {
          toast.success("Đã gửi yêu cầu sửa ước tính", { description: "Chờ điều phối viên." });
          onDone();
        },
        onError: (error) => toastError(workErrorMessage(error, "Không gửi được yêu cầu."), error),
      },
    );
  });

  return (
    <>
      <DialogHeader>
        <DialogTitle>Xin sửa ước tính: {item.label}</DialogTitle>
        <DialogDescription>
          Hiện tại {formatMinutes(item.estimate.minutes)} ({estimateBasisText(item.estimate)}).
          Điều phối viên đồng ý thì con số mới được dùng để tính tải.
        </DialogDescription>
      </DialogHeader>

      <form
        id="estimate-request"
        onSubmit={handleSubmit}
        onKeyDown={submitOnEnter}
        className="flex flex-col gap-3"
      >
        <FormField
          id="proposed-minutes"
          label="Số phút làm đề xuất"
          error={errors.proposedMinutes?.message}
        >
          <Input
            id="proposed-minutes"
            type="number"
            inputMode="numeric"
            min={1}
            max={MAX_MINUTES}
            className="w-full sm:w-40"
            {...form.register("proposedMinutes")}
          />
        </FormField>
        <FormField id="estimate-reason" label="Lý do" error={errors.reason?.message}>
          <Textarea id="estimate-reason" maxLength={MAX_REASON} {...form.register("reason")} />
          <span className="self-end text-crm-xs text-crm-text-3 tabular-nums">
            {reason.length}/{MAX_REASON}
          </span>
        </FormField>
      </form>

      <DialogFooter>
        <Button variant="outline" size="sm" onClick={onDone}>
          Hủy
        </Button>
        <Button type="submit" form="estimate-request" size="sm" disabled={request.isPending}>
          {request.isPending ? "Đang gửi..." : "Gửi yêu cầu"}
        </Button>
      </DialogFooter>
    </>
  );
};

interface Props {
  item: QueueItem | null;
  onClose: () => void;
}

/** Dialog xin sửa thời gian ước tính (R2), điều phối viên duyệt. */
export const EstimateRequestDialog = ({ item, onClose }: Props) => {
  return (
    <Dialog open={item !== null} onOpenChange={(open) => !open && onClose()}>
      <DialogContent className="sm:max-w-100">
        {item && <EstimateRequestForm key={item.id} item={item} onDone={onClose} />}
      </DialogContent>
    </Dialog>
  );
};
