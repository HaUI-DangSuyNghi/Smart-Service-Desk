import { useEffect, useMemo } from "react";
import { Controller, useForm, useWatch } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import {
  AssignmentSuggestionPanel,
  EstimateText,
  PRIORITY_LABELS,
} from "../../../platform/process";
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
  AGREE_WITH_AI,
  differsFromAi,
  intakeSuggestion,
  reviewDefaults,
  reviewSchema,
  suggestedPriority,
  toDecision,
  type ReviewInput,
  type ReviewValues,
} from "./reviewForm";
import { CATEGORY_LABELS, groupLabel } from "./ticketLabels";
import { aiSuggestion, CATEGORIES, MAX_REASON, PRIORITIES } from "./ticketRules";
import { useIntake } from "./useIntake";
import { useEstimateDefault, useReviewSuggestion, useReviewTicket } from "./useTickets";

interface FormProps {
  ticket: TicketView;
  onDone: () => void;
}

/** Lỗi duyệt theo thông điệp backend (detail) để điều phối viên biết sửa gì. */
const reviewErrorMessage = (error: unknown) => {
  if (!(error instanceof ApiError)) return "Không duyệt được.";
  const detail = error.detail ?? "";
  if (detail.includes("off duty")) return "Người được chọn vừa nghỉ trực, chọn người khác.";
  if (detail.includes("support group")) return "Người được chọn không thuộc tổ của loại sự cố này.";
  if (detail.includes("intake"))
    return "Tiếp nhận vừa thay đổi, đóng lại và mở lại để duyệt bản mới.";
  if (error.status === 409) return "Ticket không còn chờ duyệt (có thể người khác đã duyệt).";
  if (error.status === 403)
    return "Bạn không duyệt được ticket này (không tự duyệt ticket mình tạo).";
  return "Không duyệt được.";
};

const ReviewForm = ({ ticket, onDone }: FormProps) => {
  const review = useReviewTicket();
  // Ticket qua tiếp nhận: chỉ duyệt được khi đã thả sang bước duyệt, gửi kèm revision/version
  const intake = useIntake(ticket.workspace, ticket.id);
  // Gợi ý AI: kết quả chạy nền, không có thì lấy đề xuất cuối của tiếp nhận
  const intakeProposal = useMemo(
    () => intakeSuggestion(intake.data?.finalAssessment?.ai07),
    [intake.data],
  );
  const suggestion = aiSuggestion(ticket.assessment) ?? intakeProposal;
  const intakeBlocked = Boolean(intake.data && intake.data.status !== "REVIEW_RELEASED");
  const form = useForm<ReviewInput, unknown, ReviewValues>({
    resolver: zodResolver(reviewSchema),
    defaultValues: reviewDefaults(suggestion),
  });
  const [category, priority, estimateMinutes] = useWatch({
    control: form.control,
    name: ["category", "priority", "estimateMinutes"],
  });
  const minutes = Number(estimateMinutes) || 0;
  const people = useReviewSuggestion(ticket.id, {
    category,
    priority,
    estimateMinutes: minutes > 0 ? minutes : undefined,
  });
  const { errors } = form.formState;
  const matrixPriority = suggestedPriority(suggestion);
  // Tổ theo Cấu hình hệ thống, lấy từ đề xuất của backend
  const group = people.data?.group;
  // ET mặc định của backend (bảng cấu hình hoặc trung vị việc tương tự) theo loại / ưu tiên đang chọn
  const estimateDefault = useEstimateDefault(ticket.id, category, priority);
  const defaultMinutes = estimateDefault.data?.minutes;

  // Đề xuất tiếp nhận tải sau khi mở form: điền bù các ô điều phối viên chưa chọn
  useEffect(() => {
    if (!suggestion) return;
    const defaults = reviewDefaults(suggestion);
    if (defaults.category && !form.getValues("category"))
      form.setValue("category", defaults.category);
    if (defaults.priority && !form.getValues("priority"))
      form.setValue("priority", defaults.priority);
    if (defaults.reason && !form.getValues("reason")) form.setValue("reason", defaults.reason);
  }, [suggestion, form]);

  // Đổi loại / ưu tiên mà chưa tự sửa ET thì điền lại theo ET mặc định mới (R2)
  useEffect(() => {
    if (defaultMinutes === undefined || form.getFieldState("estimateMinutes").isDirty) return;
    form.setValue("estimateMinutes", defaultMinutes, { shouldValidate: true });
  }, [defaultMinutes, form]);

  const handleSubmit = form.handleSubmit((values) => {
    if (differsFromAi(values, suggestion) && values.reason === AGREE_WITH_AI) {
      form.setError("reason", { message: "Khác gợi ý của AI: ghi lý do" });
      return;
    }
    review.mutate(
      { id: ticket.id, decision: toDecision(values, suggestion, intake.data, defaultMinutes) },
      {
        onSuccess: () => {
          toast.success("Đã duyệt và giao việc", { description: ticket.title });
          onDone();
        },
        onError: (error) => toastError(reviewErrorMessage(error), error),
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
              <ChoiceCard key={item} value={item} {...form.register("category")}>
                {CATEGORY_LABELS[item]}
                {suggestion?.category === item && (
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
            {matrixPriority && (
              <> · theo bảng ảnh hưởng × độ gấp: {PRIORITY_LABELS[matrixPriority]}</>
            )}
          </legend>
          <div className="grid grid-cols-2 gap-2 sm:grid-cols-4">
            {PRIORITIES.map((item) => (
              <ChoiceCard key={item} value={item} {...form.register("priority")}>
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
          {estimateDefault.data && (
            <span className="text-crm-sm text-crm-text-3">
              Gợi ý: <EstimateText estimate={estimateDefault.data} />
            </span>
          )}
        </FormField>

        <fieldset className="flex flex-col gap-1.5">
          <legend className="mb-1 text-crm-sm text-crm-text-2">Người xử lý</legend>
          {category && priority ? (
            <Controller
              control={form.control}
              name="assignee"
              render={({ field }) => (
                <AssignmentSuggestionPanel
                  suggestion={people}
                  name={field.name}
                  value={field.value}
                  onChange={field.onChange}
                  estimateMinutes={minutes}
                />
              )}
            />
          ) : (
            <p className="text-crm-sm text-crm-text-3">
              Chọn loại sự cố và mức ưu tiên để xem đề xuất người xử lý.
            </p>
          )}
          {errors.assignee && (
            <p className="text-crm-sm text-crm-danger">{errors.assignee.message}</p>
          )}
        </fieldset>

        <FormField
          id="review-reason"
          label="Lý do duyệt"
          hint="Bắt buộc. Khác gợi ý của AI thì ghi rõ vì sao, để AI học lại."
          error={errors.reason?.message}
        >
          <Textarea id="review-reason" maxLength={MAX_REASON} {...form.register("reason")} />
        </FormField>
      </form>

      {intakeBlocked && (
        <p role="alert" className="text-crm-sm text-crm-tone-orange">
          Ticket còn ở bước tiếp nhận, chưa duyệt được. Thả ticket sang bước duyệt ở khối Tiếp nhận
          trước.
        </p>
      )}

      <DialogFooter>
        <Button variant="outline" size="sm" onClick={onDone}>
          Hủy
        </Button>
        <Button
          type="submit"
          form="review-ticket"
          size="sm"
          disabled={review.isPending || intake.isPending || intakeBlocked}
        >
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
  const open = ticket !== null && Boolean(ticket.taskId);
  return (
    <Dialog open={open} onOpenChange={(next) => !next && onClose()}>
      <DialogContent className="max-h-[90svh] overflow-y-auto sm:max-w-140">
        {ticket && open && <ReviewForm key={ticket.id} ticket={ticket} onDone={onClose} />}
      </DialogContent>
    </Dialog>
  );
};
