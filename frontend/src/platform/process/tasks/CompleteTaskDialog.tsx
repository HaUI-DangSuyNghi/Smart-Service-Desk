import { useForm, useWatch } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import type { WorkItem } from "@/platform/services";
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
import { TextSkeleton } from "@/platform/shared/ui/Skeleton";
import { Textarea } from "@/platform/shared/ui/textarea";
import { toast, toastError } from "@/platform/shared/ui/toast";
import { useProcessCatalog, useProcessLabels } from "../catalog/useProcessCatalog";
import { completeErrorMessage, isStaleTaskError } from "./taskErrors";
import { useCompleteTask } from "./useTaskActions";

// Khớp giới hạn ghi chú của backend (MAX_COMMENT)
const MAX_COMMENT = 1000;

const schema = z.object({
  outcome: z.string().min(1, "Chọn kết quả"),
  comment: z.string().max(MAX_COMMENT, `Tối đa ${MAX_COMMENT} ký tự`),
});

type FormValues = z.infer<typeof schema>;

interface FormProps {
  task: WorkItem;
  onDone: () => void;
}

const CompleteTaskForm = ({ task, onDone }: FormProps) => {
  const catalog = useProcessCatalog();
  const labels = useProcessLabels();
  const complete = useCompleteTask();
  const outcomes = labels.outcomes(task.processKey, task.key);
  const taskName = labels.taskName(task.processKey, task.key, task.name);
  // Bước chỉ có một kết quả (ví dụ "Đã xử lý") thì chọn sẵn
  const onlyOutcome = outcomes.length === 1 ? outcomes[0]?.key : undefined;

  const form = useForm<FormValues>({
    resolver: zodResolver(schema),
    defaultValues: { outcome: onlyOutcome ?? "", comment: "" },
  });
  const comment = useWatch({ control: form.control, name: "comment" });
  const { errors } = form.formState;

  const handleSubmit = form.handleSubmit(({ outcome, comment }) => {
    complete.mutate(
      { task, outcome, comment },
      {
        onSuccess: () => {
          toast.success(`Đã hoàn thành: ${taskName}`, {
            description: labels.outcomeLabel(task.processKey, outcome),
          });
          onDone();
        },
        onError: (error) => {
          toastError(completeErrorMessage(error), error);
          if (isStaleTaskError(error)) onDone();
        },
      },
    );
  });

  return (
    <>
      <DialogHeader>
        <DialogTitle>Hoàn thành bước</DialogTitle>
        <DialogDescription>
          {labels.processName(task.processKey)} · {taskName}
        </DialogDescription>
      </DialogHeader>

      <form id="complete-task" onSubmit={handleSubmit} className="flex flex-col gap-3">
        <fieldset className="flex flex-col gap-1.5">
          <legend className="mb-1 text-crm-sm text-crm-text-2">Kết quả</legend>
          {catalog.isPending && <TextSkeleton lines={2} label="Đang tải kết quả" />}
          {!catalog.isPending && outcomes.length === 0 && (
            <p className="text-crm-sm text-crm-danger">Không tải được danh sách kết quả của bước.</p>
          )}
          <div className="grid grid-cols-1 gap-2 sm:grid-cols-2">
            {outcomes.map((option) => (
              <ChoiceCard key={option.key} value={option.key} {...form.register("outcome")}>
                {option.label}
              </ChoiceCard>
            ))}
          </div>
          {errors.outcome && <p className="text-crm-sm text-crm-danger">{errors.outcome.message}</p>}
        </fieldset>

        <FormField id="comment" label="Ghi chú (không bắt buộc)" error={errors.comment?.message}>
          <Textarea id="comment" maxLength={MAX_COMMENT} {...form.register("comment")} />
          <span className="self-end text-crm-xs text-crm-text-3 tabular-nums">
            {comment.length}/{MAX_COMMENT}
          </span>
        </FormField>
      </form>

      <DialogFooter>
        <Button variant="outline" size="sm" onClick={onDone}>
          Hủy
        </Button>
        {/* Khóa nút khi đang gửi để chặn gửi trùng */}
        <Button
          type="submit"
          form="complete-task"
          size="sm"
          disabled={complete.isPending || outcomes.length === 0}
        >
          {complete.isPending ? "Đang gửi..." : "Hoàn thành"}
        </Button>
      </DialogFooter>
    </>
  );
};

interface Props {
  /** Việc cần hoàn thành; null thì dialog đóng. */
  task: WorkItem | null;
  onClose: () => void;
}

/** Dialog chọn kết quả + ghi chú để hoàn thành một bước; dùng ở công việc và trang tiến độ. */
export const CompleteTaskDialog = ({ task, onClose }: Props) => {
  return (
    <Dialog open={task !== null} onOpenChange={(open) => !open && onClose()}>
      <DialogContent className="sm:max-w-100">
        {/* key: mỗi việc một form mới, không cần reset form bằng effect */}
        {task && <CompleteTaskForm key={task.id} task={task} onDone={onClose} />}
      </DialogContent>
    </Dialog>
  );
};
