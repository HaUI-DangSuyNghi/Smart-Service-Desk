import { Controller, useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { roleLabel, useMembers } from "@/platform/human/workspace";
import type { WorkItem } from "@/platform/services";
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
import { ErrorState } from "@/platform/shared/ui/ErrorState";
import { SelectField } from "@/platform/shared/ui/SelectField";
import { TextSkeleton } from "@/platform/shared/ui/Skeleton";
import { toast, toastError } from "@/platform/shared/ui/toast";
import { useProcessLabels } from "../catalog/useProcessCatalog";
import { groupRole } from "../common/display";
import { assignErrorMessage, isTaskGoneError } from "./taskErrors";
import { useAssignTask } from "./useTaskActions";

const schema = z.object({ assignee: z.string().min(1, "Chọn người nhận việc") });
type FormValues = z.infer<typeof schema>;

interface FormProps {
  task: WorkItem;
  onDone: () => void;
}

const AssignTaskForm = ({ task, onDone }: FormProps) => {
  const labels = useProcessLabels();
  const members = useMembers();
  const assign = useAssignTask();
  const taskName = labels.taskName(task.processKey, task.key, task.name);
  // Chỉ người có vai trò thuộc nhóm xử lý của bước (backend cũng kiểm lại)
  const roles = task.candidateGroups.map(groupRole);
  const eligible = (members.data ?? []).filter((member) =>
    member.roles.some((role) => roles.includes(role)),
  );

  const form = useForm<FormValues>({
    resolver: zodResolver(schema),
    defaultValues: { assignee: task.assignee ?? "" },
  });
  const { errors } = form.formState;

  const handleSubmit = form.handleSubmit(({ assignee }) => {
    const person = eligible.find((member) => member.subject === assignee);
    assign.mutate(
      { task, assignee },
      {
        onSuccess: () => {
          toast.success(`Đã giao: ${taskName}`, {
            description: person?.fullName ?? person?.username,
          });
          onDone();
        },
        onError: (error) => {
          toastError(assignErrorMessage(error), error);
          // Chọn sai người (400/403) thì giữ dialog để chọn lại
          if (isTaskGoneError(error)) onDone();
        },
      },
    );
  });

  return (
    <>
      <DialogHeader>
        <DialogTitle>Giao việc: {taskName}</DialogTitle>
        <DialogDescription>
          {labels.processName(task.processKey)} · chỉ người thuộc nhóm{" "}
          {roles.map(roleLabel).join(", ") || "xử lý"} nhận được việc này.
        </DialogDescription>
      </DialogHeader>

      <form
        id="assign-task"
        onSubmit={handleSubmit}
        onKeyDown={submitOnEnter}
        className="flex flex-col gap-1.5"
      >
        {members.isPending && <TextSkeleton lines={3} label="Đang tải thành viên" />}
        {members.error && (
          <ErrorState error={members.error} fallback="Không tải được danh sách thành viên." />
        )}
        {members.data && eligible.length === 0 && (
          <p className="text-crm-sm text-crm-text-3">Không có ai trong nhóm xử lý của bước này.</p>
        )}
        {eligible.length > 0 && (
          <Controller
            control={form.control}
            name="assignee"
            render={({ field }) => (
              <SelectField
                aria-label="Người nhận việc"
                placeholder="Chọn người nhận việc"
                options={eligible.map((member) => ({
                  value: member.subject,
                  label: `${member.fullName ?? member.username}${member.subject === task.assignee ? " · đang giữ" : ""}`,
                }))}
                value={field.value}
                onValueChange={field.onChange}
                className="h-9 w-full max-w-none sm:h-8"
              />
            )}
          />
        )}
        {errors.assignee && (
          <p className="text-crm-sm text-crm-danger">{errors.assignee.message}</p>
        )}
      </form>

      <DialogFooter>
        <Button variant="outline" size="sm" onClick={onDone}>
          Hủy
        </Button>
        {/* Khóa nút khi đang gửi để chặn gửi trùng */}
        <Button
          type="submit"
          form="assign-task"
          size="sm"
          disabled={assign.isPending || eligible.length === 0}
        >
          {assign.isPending ? "Đang giao..." : "Giao việc"}
        </Button>
      </DialogFooter>
    </>
  );
};

interface Props {
  /** Việc cần giao; null thì dialog đóng. */
  task: WorkItem | null;
  onClose: () => void;
}

/** Dialog chọn người nhận việc (điều phối viên); dùng ở trang Công việc và trang tiến độ. */
export const AssignTaskDialog = ({ task, onClose }: Props) => {
  return (
    <Dialog open={task !== null} onOpenChange={(open) => !open && onClose()}>
      <DialogContent className="sm:max-w-100">
        {/* key: mỗi việc một form mới, không cần reset form bằng effect */}
        {task && <AssignTaskForm key={task.id} task={task} onDone={onClose} />}
      </DialogContent>
    </Dialog>
  );
};
