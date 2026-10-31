import {
  IconBell,
  IconCalendarTime,
  IconCircleCheck,
  IconHourglass,
  IconRulerMeasure,
  IconUserCheck,
} from "@tabler/icons-react";
import { get, useForm, type FieldErrors } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import type { Priority, WorkSettings } from "@/platform/services";
import { ApiError } from "@/platform/shared/api";
import { formatDateTime } from "@/platform/shared/lib/format";
import { Button } from "@/platform/shared/ui/button";
import { ErrorState } from "@/platform/shared/ui/ErrorState";
import { FieldGroup } from "@/platform/shared/ui/FieldGroup";
import { FormField } from "@/platform/shared/ui/FormField";
import { Input } from "@/platform/shared/ui/input";
import { FieldsSkeleton } from "@/platform/shared/ui/Skeleton";
import { toast, toastError } from "@/platform/shared/ui/toast";
import { PRIORITY_LABELS } from "../work/workLabels";
import { useSaveWorkSettings, useWorkSettings } from "./useWorkSettings";
import {
  ALERT_FIELDS,
  ASSIGNMENT_FIELDS,
  CLOSING_FIELDS,
  ESTIMATE_FIELDS,
  SLA_FIELDS,
  workSettingsSchema,
  type NumberFieldDef,
  type WorkSettingsInput,
  type WorkSettingsValues,
} from "./workSettingsForm";

const PRIORITIES: Priority[] = ["CRITICAL", "HIGH", "MEDIUM", "LOW"];

const errorOf = (errors: FieldErrors<WorkSettingsInput>, path: string): string | undefined =>
  get(errors, path)?.message;

const WorkSettingsForm = ({ settings }: { settings: WorkSettings }) => {
  const save = useSaveWorkSettings();
  const form = useForm<WorkSettingsInput, unknown, WorkSettingsValues>({
    resolver: zodResolver(workSettingsSchema),
    defaultValues: settings,
  });
  const { errors } = form.formState;

  const handleSubmit = form.handleSubmit((values) => {
    save.mutate(values, {
      onSuccess: () => toast.success("Đã lưu cấu hình chung"),
      onError: (error) =>
        toastError(
          error instanceof ApiError && error.status === 409
            ? "Người khác vừa sửa cấu hình. Đã tải bản mới, hãy sửa lại."
            : "Không lưu được cấu hình.",
          error,
        ),
    });
  });

  const numberFields = (fields: readonly NumberFieldDef[]) => (
    <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
      {fields.map((field) => (
        <FormField
          key={field.path}
          id={field.path}
          label={field.label}
          hint={field.hint}
          error={errorOf(errors, field.path)}
        >
          <Input
            id={field.path}
            type="number"
            inputMode="decimal"
            step={field.step ?? 1}
            className="w-full sm:w-40"
            {...form.register(field.path)}
          />
        </FormField>
      ))}
    </div>
  );

  return (
    <form onSubmit={handleSubmit} className="flex flex-col gap-8">
      <p className="text-crm-sm text-crm-text-2">
        Lưu xong áp dụng ngay cho thao tác sau đó (duyệt, giao việc, bắt đầu, cảnh báo). Ticket đã
        duyệt giữ nguyên hạn xử lý và ước tính đã tính lúc duyệt.
      </p>
      <FieldGroup title="Giờ làm việc" icon={IconCalendarTime}>
        <p className="text-crm-sm text-crm-text-3">
          Dùng để tính hạn xử lý (trừ mức Khẩn cấp tính liên tục). Lịch ca của từng người xếp ở
          trang Ca trực.
        </p>
        {settings.shifts.map((shift, index) => (
          <div key={shift.name} className="grid grid-cols-1 gap-2 sm:grid-cols-3">
            <FormField
              id={`shift-${index}-name`}
              label="Tên ca"
              error={errorOf(errors, `shifts.${index}.name`)}
            >
              <Input id={`shift-${index}-name`} {...form.register(`shifts.${index}.name`)} />
            </FormField>
            <FormField
              id={`shift-${index}-start`}
              label="Bắt đầu"
              error={errorOf(errors, `shifts.${index}.start`)}
            >
              <Input
                id={`shift-${index}-start`}
                type="time"
                {...form.register(`shifts.${index}.start`)}
              />
            </FormField>
            <FormField
              id={`shift-${index}-end`}
              label="Kết thúc"
              error={errorOf(errors, `shifts.${index}.end`)}
            >
              <Input
                id={`shift-${index}-end`}
                type="time"
                {...form.register(`shifts.${index}.end`)}
              />
            </FormField>
          </div>
        ))}
      </FieldGroup>

      <FieldGroup title="Phân công" icon={IconUserCheck}>
        {numberFields(ASSIGNMENT_FIELDS)}
      </FieldGroup>

      <FieldGroup title="Ước tính" icon={IconRulerMeasure}>
        {numberFields(ESTIMATE_FIELDS)}
      </FieldGroup>

      <FieldGroup title="Hạn xử lý" icon={IconHourglass}>
        <div className="flex flex-col gap-2">
          {PRIORITIES.map((priority) => (
            <div key={priority} className="grid grid-cols-1 gap-2 sm:grid-cols-[8rem_1fr_1fr]">
              <span className="self-center text-crm-text-2">{PRIORITY_LABELS[priority]}</span>
              <FormField
                id={`sla-${priority}-start`}
                label="Bắt đầu trong (phút)"
                error={errorOf(errors, `sla.targets.${priority}.startWithinMinutes`)}
              >
                <Input
                  id={`sla-${priority}-start`}
                  type="number"
                  inputMode="numeric"
                  {...form.register(`sla.targets.${priority}.startWithinMinutes`)}
                />
              </FormField>
              <FormField
                id={`sla-${priority}-resolve`}
                label="Xong trong (phút)"
                error={errorOf(errors, `sla.targets.${priority}.resolveWithinMinutes`)}
              >
                <Input
                  id={`sla-${priority}-resolve`}
                  type="number"
                  inputMode="numeric"
                  {...form.register(`sla.targets.${priority}.resolveWithinMinutes`)}
                />
              </FormField>
            </div>
          ))}
        </div>
        {numberFields(SLA_FIELDS)}
        <label className="flex items-center gap-2 text-crm-text">
          <input
            type="checkbox"
            className="size-4 accent-crm-accent"
            {...form.register("sla.waitingExternalCounts")}
          />
          Tính cả thời gian chờ bên ngoài
        </label>
      </FieldGroup>

      <FieldGroup title="Cảnh báo" icon={IconBell}>
        {numberFields(ALERT_FIELDS)}
      </FieldGroup>

      <FieldGroup title="Đóng ticket" icon={IconCircleCheck}>
        {numberFields(CLOSING_FIELDS)}
      </FieldGroup>

      <div className="flex flex-wrap items-center justify-between gap-2">
        {settings.updatedAt && (
          <p className="text-crm-sm text-crm-text-3">
            Sửa lần cuối {formatDateTime(settings.updatedAt)} · phiên bản {settings.version}
          </p>
        )}
        <Button type="submit" size="sm" disabled={save.isPending || !form.formState.isDirty}>
          {save.isPending ? "Đang lưu..." : "Lưu cấu hình chung"}
        </Button>
      </div>
    </form>
  );
};

/** Cấu hình chung của Tầng 2 (mục 6): chọn người, ca trực, hạn xử lý, cảnh báo, ước tính. */
export const WorkSettingsSection = () => {
  const settings = useWorkSettings();

  if (settings.isPending) return <FieldsSkeleton rows={8} label="Đang tải cấu hình" />;
  if (settings.error)
    return <ErrorState error={settings.error} fallback="Không tải được cấu hình." />;
  // key theo version: lưu xong hoặc người khác sửa thì form lấy giá trị mới, không cần reset bằng effect
  return <WorkSettingsForm key={settings.data.version} settings={settings.data} />;
};
