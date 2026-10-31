import { useForm, useWatch } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { PRIORITY_LABELS } from "../../../platform/process";
import { submitOnEnter } from "../../../platform/shared/lib/submitOnEnter";
import { Button } from "../../../platform/shared/ui/button";
import { ChoiceCard } from "../../../platform/shared/ui/ChoiceCard";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "../../../platform/shared/ui/dialog";
import { FormField } from "../../../platform/shared/ui/FormField";
import { Textarea } from "../../../platform/shared/ui/textarea";
import { toast, toastError } from "../../../platform/shared/ui/toast";
import type { Classification, TicketView } from "../services/ticketService";
import { CATEGORY_LABELS } from "./ticketLabels";
import { CATEGORIES, MAX_REASON, PRIORITIES, type Category } from "./ticketRules";
import { useCorrectClassification } from "./useTickets";

const schema = z.object({
  category: z.enum(["HARDWARE", "NETWORK", "SOFTWARE", "ACCOUNT", "OTHER"]),
  priority: z.enum(["LOW", "MEDIUM", "HIGH", "CRITICAL"]),
  reason: z.string().trim().min(1, "Ghi lý do sửa").max(MAX_REASON, `Tối đa ${MAX_REASON} ký tự`),
});

type FormValues = z.infer<typeof schema>;

interface FormProps {
  ticket: TicketView;
  classification: Classification & { category: Category };
  onDone: () => void;
}

const CorrectForm = ({ ticket, classification, onDone }: FormProps) => {
  const correct = useCorrectClassification();
  const form = useForm<FormValues>({
    resolver: zodResolver(schema),
    defaultValues: {
      category: classification.category,
      priority: classification.priority,
      reason: "",
    },
  });
  const category = useWatch({ control: form.control, name: "category" });
  const { errors } = form.formState;
  // R9: đổi loại khi chưa bắt đầu thì đổi tổ, ticket quay về hàng chờ duyệt
  const willReturnToReview =
    category !== classification.category &&
    (ticket.state === "ASSIGNED" || ticket.state === "REOPENED");

  const handleSubmit = form.handleSubmit(({ category, priority, reason }) => {
    if (category === classification.category && priority === classification.priority) {
      form.setError("category", { message: "Chưa thay đổi gì" });
      return;
    }
    correct.mutate(
      {
        id: ticket.id,
        correction: {
          // Chỉ gửi trường đã đổi
          category: category !== classification.category ? category : undefined,
          priority: priority !== classification.priority ? priority : undefined,
          reason,
        },
      },
      {
        onSuccess: () => {
          toast.success("Đã sửa phân loại", { description: ticket.title });
          onDone();
        },
        onError: (error) => toastError("Không sửa được phân loại.", error),
      },
    );
  });

  return (
    <>
      <DialogHeader>
        <DialogTitle>Sửa phân loại</DialogTitle>
        <DialogDescription>Mọi lần sửa đều được lưu kèm lý do và người sửa.</DialogDescription>
      </DialogHeader>

      <form
        id="correct-classification"
        onSubmit={handleSubmit}
        onKeyDown={submitOnEnter}
        className="flex flex-col gap-4"
      >
        <fieldset className="flex flex-col gap-1.5">
          <legend className="mb-1 text-crm-sm text-crm-text-2">Loại sự cố</legend>
          <div className="grid grid-cols-2 gap-2 sm:grid-cols-3">
            {CATEGORIES.map((item) => (
              <ChoiceCard key={item} value={item} {...form.register("category")}>
                {CATEGORY_LABELS[item]}
              </ChoiceCard>
            ))}
          </div>
          {errors.category && (
            <p className="text-crm-sm text-crm-danger">{errors.category.message}</p>
          )}
          {willReturnToReview && (
            <p className="text-crm-sm text-crm-tone-orange">
              Đổi loại sẽ đổi tổ xử lý: ticket quay về hàng chờ duyệt để giao lại.
            </p>
          )}
        </fieldset>

        <fieldset className="flex flex-col gap-1.5">
          <legend className="mb-1 text-crm-sm text-crm-text-2">Mức ưu tiên</legend>
          <div className="grid grid-cols-2 gap-2 sm:grid-cols-4">
            {PRIORITIES.map((item) => (
              <ChoiceCard key={item} value={item} {...form.register("priority")}>
                {PRIORITY_LABELS[item]}
              </ChoiceCard>
            ))}
          </div>
        </fieldset>

        <FormField id="correct-reason" label="Lý do" error={errors.reason?.message}>
          <Textarea id="correct-reason" maxLength={MAX_REASON} {...form.register("reason")} />
        </FormField>
      </form>

      <DialogFooter>
        <Button variant="outline" size="sm" onClick={onDone}>
          Hủy
        </Button>
        <Button type="submit" form="correct-classification" size="sm" disabled={correct.isPending}>
          {correct.isPending ? "Đang lưu..." : "Lưu"}
        </Button>
      </DialogFooter>
    </>
  );
};

interface Props {
  ticket: TicketView | null;
  onClose: () => void;
}

/** Kỹ thuật viên đang giữ hoặc điều phối viên sửa loại sự cố / ưu tiên (R9). */
export const CorrectClassificationDialog = ({ ticket, onClose }: Props) => {
  // Chỉ sửa phân loại đã duyệt (luôn có loại)
  const current = ticket?.classification;
  const classification = current?.category ? { ...current, category: current.category } : null;
  return (
    <Dialog
      open={ticket !== null && classification !== null}
      onOpenChange={(open) => !open && onClose()}
    >
      <DialogContent className="sm:max-w-120">
        {ticket && classification && (
          <CorrectForm
            key={ticket.id}
            ticket={ticket}
            classification={classification}
            onDone={onClose}
          />
        )}
      </DialogContent>
    </Dialog>
  );
};
