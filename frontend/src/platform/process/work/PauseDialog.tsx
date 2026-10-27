import { useForm, useWatch } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import type { PendingReason, QueueItem } from "@/platform/services";
import { submitOnEnter } from "@/platform/shared/lib/submitOnEnter";
import { Button } from "@/platform/shared/ui/button";
import { ChoiceCard } from "@/platform/shared/ui/ChoiceCard";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/platform/shared/ui/dialog";
import { FormField } from "@/platform/shared/ui/FormField";
import { Textarea } from "@/platform/shared/ui/textarea";
import { toast, toastError } from "@/platform/shared/ui/toast";
import { usePauseWork } from "./useWork";
import { workErrorMessage } from "./workErrors";
import { PENDING_REASON_LABELS } from "./workLabels";

const MAX_NOTE = 1000;
const REASONS = Object.keys(PENDING_REASON_LABELS) as PendingReason[];

// Lý do "Khác" phải ghi rõ để người khác hiểu vì sao việc dừng
const schema = z
  .object({
    reason: z.enum(["WAITING_REQUESTER", "WAITING_PARTS", "WAITING_EXTERNAL", "OTHER"], {
      message: "Chọn lý do",
    }),
    note: z.string().max(MAX_NOTE, `Tối đa ${MAX_NOTE} ký tự`),
  })
  .refine((value) => value.reason !== "OTHER" || value.note.trim().length > 0, {
    path: ["note"],
    message: "Ghi rõ lý do",
  });

type FormValues = z.infer<typeof schema>;

interface FormProps {
  item: QueueItem;
  onDone: () => void;
}

const PauseForm = ({ item, onDone }: FormProps) => {
  const pause = usePauseWork();
  const form = useForm<FormValues>({
    resolver: zodResolver(schema),
    defaultValues: { note: "" },
  });
  const note = useWatch({ control: form.control, name: "note" });
  const { errors } = form.formState;

  const handleSubmit = form.handleSubmit(({ reason, note }) => {
    pause.mutate(
      { taskId: item.id, input: { reason, note: note.trim() || undefined } },
      {
        onSuccess: () => {
          toast.success("Đã tạm dừng", { description: PENDING_REASON_LABELS[reason] });
          onDone();
        },
        onError: (error) => toastError(workErrorMessage(error, "Không tạm dừng được."), error),
      },
    );
  });

  return (
    <>
      <DialogHeader>
        <DialogTitle>Tạm dừng: {item.label}</DialogTitle>
        <DialogDescription>
          Việc chuyển sang Đang chờ. Chờ người yêu cầu trả lời thì không tính vào hạn xử lý.
        </DialogDescription>
      </DialogHeader>

      <form
        id="pause-work"
        onSubmit={handleSubmit}
        onKeyDown={submitOnEnter}
        className="flex flex-col gap-3"
      >
        <fieldset className="flex flex-col gap-1.5">
          <legend className="mb-1 text-crm-sm text-crm-text-2">Lý do</legend>
          {REASONS.map((reason) => (
            <ChoiceCard key={reason} value={reason} {...form.register("reason")}>
              {PENDING_REASON_LABELS[reason]}
            </ChoiceCard>
          ))}
          {errors.reason && <p className="text-crm-sm text-crm-danger">{errors.reason.message}</p>}
        </fieldset>

        <FormField id="pause-note" label="Ghi chú" error={errors.note?.message}>
          <Textarea id="pause-note" maxLength={MAX_NOTE} {...form.register("note")} />
          <span className="self-end text-crm-xs text-crm-text-3 tabular-nums">
            {note.length}/{MAX_NOTE}
          </span>
        </FormField>
      </form>

      <DialogFooter>
        <Button variant="outline" size="sm" onClick={onDone}>
          Hủy
        </Button>
        <Button type="submit" form="pause-work" size="sm" disabled={pause.isPending}>
          {pause.isPending ? "Đang gửi..." : "Tạm dừng"}
        </Button>
      </DialogFooter>
    </>
  );
};

interface Props {
  /** Việc cần tạm dừng; null thì dialog đóng. */
  item: QueueItem | null;
  onClose: () => void;
}

/** Dialog chọn lý do tạm dừng (R6: Đang làm → Đang chờ). */
export const PauseDialog = ({ item, onClose }: Props) => {
  return (
    <Dialog open={item !== null} onOpenChange={(open) => !open && onClose()}>
      <DialogContent className="sm:max-w-100">
        {/* key: mỗi việc một form mới, không cần reset form bằng effect */}
        {item && <PauseForm key={item.id} item={item} onDone={onClose} />}
      </DialogContent>
    </Dialog>
  );
};
