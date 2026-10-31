import { useFieldArray, useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { IconPlus, IconTrash } from "@tabler/icons-react";
import type { AgentSchedule } from "@/platform/services";
import { ApiError } from "@/platform/shared/api";
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
import { toast, toastError } from "@/platform/shared/ui/toast";
import {
  MAX_SHIFTS,
  scheduleSchema,
  scheduleValues,
  toLocalInput,
  toSchedule,
  type ScheduleValues,
} from "./scheduleForm";
import { useUpdateSchedule } from "./useDuty";

const HOUR_MS = 60 * 60 * 1000;

/** Ca mới: tiếp sau ca cuối (hoặc từ giờ tròn kế tiếp), dài 8 tiếng. */
const nextBlankShift = (shifts: ScheduleValues["shifts"]) => {
  const last = shifts.at(-1);
  const start = last?.endsAt
    ? new Date(last.endsAt)
    : new Date(Math.ceil(Date.now() / HOUR_MS) * HOUR_MS);
  return {
    startsAt: toLocalInput(start.toISOString()),
    endsAt: toLocalInput(new Date(start.getTime() + 8 * HOUR_MS).toISOString()),
  };
};

interface FormProps {
  schedule: AgentSchedule;
  name: string;
  onDone: () => void;
}

const ScheduleForm = ({ schedule, name, onDone }: FormProps) => {
  const update = useUpdateSchedule();
  const form = useForm<ScheduleValues>({
    resolver: zodResolver(scheduleSchema),
    defaultValues: scheduleValues(schedule),
  });
  const shifts = useFieldArray({ control: form.control, name: "shifts" });
  const { errors } = form.formState;

  const handleSubmit = form.handleSubmit((values) => {
    update.mutate(toSchedule(schedule, values), {
      onSuccess: () => {
        toast.success("Đã lưu lịch ca", { description: name });
        onDone();
      },
      onError: (error) =>
        toastError(
          error instanceof ApiError && error.status === 409
            ? "Người khác vừa sửa lịch này, đóng lại và mở lại để xem bản mới."
            : "Không lưu được lịch ca.",
          error,
        ),
    });
  });

  return (
    <>
      <DialogHeader>
        <DialogTitle>Lịch ca: {name}</DialogTitle>
        <DialogDescription>
          Bật lịch thì người này chỉ được tính là đang trực trong các ca dưới đây (và khi công tắc
          trực của họ đang bật). Giờ theo múi giờ của máy bạn.
        </DialogDescription>
      </DialogHeader>

      <form id="agent-schedule" onSubmit={handleSubmit} className="flex flex-col gap-3">
        <label className="flex items-center gap-2">
          <input type="checkbox" className="size-4" {...form.register("enabled")} />
          Dùng lịch ca
        </label>

        <fieldset className="flex flex-col gap-2">
          <legend className="mb-1 text-crm-sm text-crm-text-2">Các ca ({shifts.fields.length})</legend>
          {shifts.fields.length === 0 && (
            <p className="text-crm-sm text-crm-text-3">Chưa có ca nào.</p>
          )}
          {shifts.fields.map((field, index) => (
            // Điện thoại: hai ô xếp dọc; màn rộng: cùng hàng
            <div key={field.id} className="flex flex-col gap-2 sm:flex-row sm:items-start">
              <FormField
                id={`shift-${index}-start`}
                label="Bắt đầu"
                error={errors.shifts?.[index]?.startsAt?.message}
              >
                <Input
                  id={`shift-${index}-start`}
                  type="datetime-local"
                  {...form.register(`shifts.${index}.startsAt`)}
                />
              </FormField>
              <FormField
                id={`shift-${index}-end`}
                label="Kết thúc"
                error={errors.shifts?.[index]?.endsAt?.message}
              >
                <Input
                  id={`shift-${index}-end`}
                  type="datetime-local"
                  {...form.register(`shifts.${index}.endsAt`)}
                />
              </FormField>
              <Button
                type="button"
                variant="ghost"
                size="icon-sm"
                aria-label={`Xóa ca ${index + 1}`}
                className="size-9 self-end md:size-7"
                onClick={() => shifts.remove(index)}
              >
                <IconTrash size={16} stroke={2} />
              </Button>
            </div>
          ))}
          {errors.shifts?.message && (
            <p className="text-crm-sm text-crm-danger">{errors.shifts.message}</p>
          )}
          <Button
            type="button"
            variant="outline"
            size="sm"
            className="self-start"
            disabled={shifts.fields.length >= MAX_SHIFTS}
            onClick={() => shifts.append(nextBlankShift(form.getValues("shifts")))}
          >
            <IconPlus size={14} stroke={2} />
            Thêm ca
          </Button>
        </fieldset>
      </form>

      <DialogFooter>
        <Button variant="outline" size="sm" onClick={onDone}>
          Hủy
        </Button>
        <Button type="submit" form="agent-schedule" size="sm" disabled={update.isPending}>
          {update.isPending ? "Đang lưu..." : "Lưu"}
        </Button>
      </DialogFooter>
    </>
  );
};

interface Props {
  /** Lịch đang sửa; null thì dialog đóng. */
  schedule: AgentSchedule | null;
  name: string;
  onClose: () => void;
}

/** Điều phối viên đặt lịch ca cho một kỹ thuật viên (R3). */
export const ScheduleDialog = ({ schedule, name, onClose }: Props) => (
  <Dialog open={schedule !== null} onOpenChange={(open) => !open && onClose()}>
    <DialogContent className="max-h-[90svh] overflow-y-auto sm:max-w-140">
      {/* key: mỗi người một form mới, không cần reset bằng effect */}
      {schedule && (
        <ScheduleForm
          key={`${schedule.subject}/${schedule.version}`}
          schedule={schedule}
          name={name}
          onDone={onClose}
        />
      )}
    </DialogContent>
  </Dialog>
);
