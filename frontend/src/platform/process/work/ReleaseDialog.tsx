import { useForm, useWatch } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import type { QueueItem, ReleaseReason } from "@/platform/services";
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
import { useReleaseWork } from "./useWork";
import { workErrorMessage } from "./workErrors";
import { RELEASE_REASON_LABELS } from "./workLabels";

const MAX_NOTE = 1000;
const REASONS = Object.keys(RELEASE_REASON_LABELS) as ReleaseReason[];

const schema = z.object({
  reason: z.enum(["WRONG_GROUP", "HANDOVER", "OTHER"], { message: "Chọn lý do" }),
  note: z
    .string()
    .trim()
    .min(1, "Ghi rõ lý do, bàn giao thì ghi đã làm đến đâu")
    .max(MAX_NOTE, `Tối đa ${MAX_NOTE} ký tự`),
});

type FormValues = z.infer<typeof schema>;

interface FormProps {
  item: QueueItem;
  onDone: () => void;
}

const ReleaseForm = ({ item, onDone }: FormProps) => {
  const release = useReleaseWork();
  const form = useForm<FormValues>({
    resolver: zodResolver(schema),
    defaultValues: { note: "" },
  });
  const note = useWatch({ control: form.control, name: "note" });
  const { errors } = form.formState;

  const handleSubmit = form.handleSubmit((input) => {
    release.mutate(
      { taskId: item.id, input },
      {
        onSuccess: () => {
          toast.success("Đã trả việc", { description: "Điều phối viên sẽ giao lại." });
          onDone();
        },
        onError: (error) => toastError(workErrorMessage(error, "Không trả được việc."), error),
      },
    );
  });

  return (
    <>
      <DialogHeader>
        <DialogTitle>Trả việc</DialogTitle>
        <DialogDescription className="truncate font-medium text-crm-text">
          {item.label}
        </DialogDescription>
      </DialogHeader>

      <form
        id="release-work"
        onSubmit={handleSubmit}
        onKeyDown={submitOnEnter}
        className="flex flex-col gap-4"
      >
        <p className="leading-relaxed text-crm-text-2">
          Việc quay về hàng chờ duyệt để điều phối viên giao lại; thời gian đã làm được giữ.
        </p>
        <fieldset className="flex flex-col gap-1.5">
          <legend className="mb-1 text-crm-sm text-crm-text-2">Lý do</legend>
          {REASONS.map((reason) => (
            <ChoiceCard key={reason} value={reason} {...form.register("reason")}>
              {RELEASE_REASON_LABELS[reason]}
            </ChoiceCard>
          ))}
          {errors.reason && <p className="text-crm-sm text-crm-danger">{errors.reason.message}</p>}
        </fieldset>

        <FormField id="release-note" label="Ghi chú" error={errors.note?.message}>
          <Textarea id="release-note" maxLength={MAX_NOTE} {...form.register("note")} />
          <span className="self-end text-crm-xs text-crm-text-3 tabular-nums">
            {note.length}/{MAX_NOTE}
          </span>
        </FormField>
      </form>

      <DialogFooter>
        <Button variant="outline" size="sm" onClick={onDone}>
          Hủy
        </Button>
        <Button type="submit" form="release-work" size="sm" disabled={release.isPending}>
          {release.isPending ? "Đang gửi..." : "Trả việc"}
        </Button>
      </DialogFooter>
    </>
  );
};

interface Props {
  item: QueueItem | null;
  onClose: () => void;
}

/** Dialog trả việc về điều phối viên (R6: sai tổ; R3: hết ca bàn giao). */
export const ReleaseDialog = ({ item, onClose }: Props) => {
  return (
    <Dialog open={item !== null} onOpenChange={(open) => !open && onClose()}>
      <DialogContent className="sm:max-w-100">
        {item && <ReleaseForm key={item.id} item={item} onDone={onClose} />}
      </DialogContent>
    </Dialog>
  );
};
