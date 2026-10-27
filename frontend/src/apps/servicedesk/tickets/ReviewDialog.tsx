import { Controller, useForm, useWatch } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { AssignmentSuggestionPanel, PRIORITY_LABELS } from "../../../platform/process";
import { ApiError } from "../../../platform/shared/api";
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
import { Input } from "../../../platform/shared/ui/input";
import { Textarea } from "../../../platform/shared/ui/textarea";
import { toast, toastError } from "../../../platform/shared/ui/toast";
import type { TicketView } from "../services/ticketService";
import {
  differsFromAi,
  reviewDefaults,
  reviewSchema,
  suggestedPriority,
  toDecision,
  type ReviewInput,
  type ReviewValues,
} from "./reviewForm";
import { CATEGORY_LABELS, groupLabel } from "./ticketLabels";
import { CATEGORIES, DEFAULT_CATEGORY_GROUPS, DEFAULT_ESTIMATES, MAX_REASON, PRIORITIES } from "./ticketRules";
import { useReviewTicket } from "./useTickets";

interface FormProps {
  ticket: TicketView;
  taskId: string;
  onDone: () => void;
}

const ReviewForm = ({ ticket, taskId, onDone }: FormProps) => {
  const review = useReviewTicket();
  const { assessment } = ticket;
  const form = useForm<ReviewInput, unknown, ReviewValues>({
    resolver: zodResolver(reviewSchema),
    defaultValues: reviewDefaults(assessment),
  });
  const [category, estimateMinutes] = useWatch({
    control: form.control,
    name: ["category", "estimateMinutes"],
  });
  const { errors } = form.formState;
  const matrixPriority = suggestedPriority(assessment);
  const group = category ? DEFAULT_CATEGORY_GROUPS[category] : null;

  // Đổi loại hoặc ưu tiên mà chưa tự sửa ET thì điền lại ET theo bảng mặc định (R2)
  const handleClassificationChange = () => {
    const values = form.getValues();
    if (!values.category || !values.priority || form.getFieldState("estimateMinutes").isDirty) return;
    form.setValue("estimateMinutes", DEFAULT_ESTIMATES[values.category][values.priority]);
  };

  const handleSubmit = form.handleSubmit((values) => {
    if (differsFromAi(values, assessment) && !values.reason) {
      form.setError("reason", { message: "Khác gợi ý của AI: ghi lý do" });
      return;
    }
    review.mutate(
      { id: ticket.id, decision: toDecision(values, assessment) },
      {
        onSuccess: () => {
          toast.success("Đã duyệt và giao việc", { description: ticket.title });
          onDone();
        },
        onError: (error) => {
          const isTaken = error instanceof ApiError && error.status === 409;
          toastError(isTaken ? "Người khác đã duyệt ticket này." : "Không duyệt được.", error);
          if (isTaken) onDone();
        },
      },
    );
  });

  return (
    <>
      <DialogHeader>
        <DialogTitle>Duyệt và giao việc</DialogTitle>
        <DialogDescription className="truncate">{ticket.title}</DialogDescription>
      </DialogHeader>

      <form id="review-ticket" onSubmit={handleSubmit} className="flex flex-col gap-4">
        <fieldset className="flex flex-col gap-1.5">
          <legend className="mb-1 text-crm-sm text-crm-text-2">
            Loại sự cố{group && <> · tổ {groupLabel(group)}</>}
          </legend>
          <div className="grid grid-cols-2 gap-2 sm:grid-cols-3">
            {CATEGORIES.map((item) => (
              <ChoiceCard
                key={item}
                value={item}
                {...form.register("category", { onChange: handleClassificationChange })}
              >
                {CATEGORY_LABELS[item]}
                {assessment?.category === item && (
                  <span className="text-crm-xs text-crm-accent"> · AI</span>
                )}
              </ChoiceCard>
            ))}
          </div>
          {errors.category && (
            <p className="text-crm-sm text-crm-danger">{errors.category.message}</p>
          )}
        </fieldset>

        <fieldset className="flex flex-col gap-1.5">
          <legend className="mb-1 text-crm-sm text-crm-text-2">
            Mức ưu tiên
            {matrixPriority && <> · theo bảng ảnh hưởng × độ gấp: {PRIORITY_LABELS[matrixPriority]}</>}
          </legend>
          <div className="grid grid-cols-2 gap-2 sm:grid-cols-4">
            {PRIORITIES.map((item) => (
              <ChoiceCard
                key={item}
                value={item}
                {...form.register("priority", { onChange: handleClassificationChange })}
              >
                {PRIORITY_LABELS[item]}
              </ChoiceCard>
            ))}
          </div>
          {errors.priority && (
            <p className="text-crm-sm text-crm-danger">{errors.priority.message}</p>
          )}
        </fieldset>

        <FormField
          id="review-estimate"
          label="Ước tính (phút làm)"
          error={errors.estimateMinutes?.message}
        >
          <Input
            id="review-estimate"
            type="number"
            inputMode="numeric"
            min={1}
            className="w-full sm:w-40"
            {...form.register("estimateMinutes")}
          />
        </FormField>

        <fieldset className="flex flex-col gap-1.5">
          <legend className="mb-1 text-crm-sm text-crm-text-2">Người xử lý</legend>
          <Controller
            control={form.control}
            name="assignee"
            render={({ field }) => (
              <AssignmentSuggestionPanel
                taskId={taskId}
                name={field.name}
                value={field.value}
                onChange={field.onChange}
                estimateMinutes={Number(estimateMinutes) || 0}
              />
            )}
          />
          {errors.assignee && (
            <p className="text-crm-sm text-crm-danger">{errors.assignee.message}</p>
          )}
        </fieldset>

        <FormField
          id="review-reason"
          label="Lý do (bắt buộc khi khác gợi ý AI)"
          error={errors.reason?.message}
        >
          <Textarea id="review-reason" maxLength={MAX_REASON} {...form.register("reason")} />
        </FormField>
      </form>

      <DialogFooter>
        <Button variant="outline" size="sm" onClick={onDone}>
          Hủy
        </Button>
        <Button type="submit" form="review-ticket" size="sm" disabled={review.isPending}>
          {review.isPending ? "Đang duyệt..." : "Duyệt và giao việc"}
        </Button>
      </DialogFooter>
    </>
  );
};

interface Props {
  /** Ticket đang chờ duyệt; null thì dialog đóng. */
  ticket: TicketView | null;
  onClose: () => void;
}

/** Dialog duyệt ticket (R4): chốt loại, ưu tiên, ước tính và người xử lý trong một lần. */
export const ReviewDialog = ({ ticket, onClose }: Props) => {
  const taskId = ticket?.taskId ?? null;
  return (
    <Dialog open={ticket !== null && taskId !== null} onOpenChange={(open) => !open && onClose()}>
      <DialogContent className="sm:max-w-140">
        {ticket && taskId && (
          <ReviewForm key={ticket.id} ticket={ticket} taskId={taskId} onDone={onClose} />
        )}
      </DialogContent>
    </Dialog>
  );
};
