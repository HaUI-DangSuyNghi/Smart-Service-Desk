import { IconPlus, IconTrash } from "@tabler/icons-react";
import { get, useFieldArray, type UseFormReturn } from "react-hook-form";
import { Button } from "@/platform/shared/ui/button";
import { Input } from "@/platform/shared/ui/input";
import type { WorkSettingsInput, WorkSettingsValues } from "./workSettingsForm";

interface Props {
  form: UseFormReturn<WorkSettingsInput, unknown, WorkSettingsValues>;
}

/** Danh sách ngày lễ được nghỉ (ngày + ghi chú hiện trên lịch trực); dùng chung cho hạn SLA và ca trực. */
export const HolidaysField = ({ form }: Props) => {
  const holidays = useFieldArray({ control: form.control, name: "schedule.holidays" });
  const errors = form.formState.errors;
  const listError =
    get(errors, "schedule.holidays")?.root?.message ?? get(errors, "schedule.holidays")?.message;

  return (
    <div className="flex flex-col gap-2">
      <span className="text-crm-sm text-crm-text-2">Ngày lễ</span>
      {holidays.fields.map((field, index) => {
        const dateError = get(errors, `schedule.holidays.${index}.date`)?.message;
        const nameError = get(errors, `schedule.holidays.${index}.name`)?.message;
        return (
          // Điện thoại: ngày và tên xếp dọc; màn rộng: một hàng
          <div key={field.id} className="flex flex-col gap-1">
            <div className="flex flex-wrap items-center gap-2 sm:flex-nowrap">
              <Input
                type="date"
                aria-label={`Ngày lễ ${index + 1}`}
                className="w-full sm:w-44"
                {...form.register(`schedule.holidays.${index}.date`)}
              />
              <Input
                aria-label={`Tên ngày lễ ${index + 1}`}
                placeholder="Ví dụ: Quốc khánh"
                maxLength={100}
                className="min-w-0 flex-1"
                {...form.register(`schedule.holidays.${index}.name`)}
              />
              <Button
                type="button"
                variant="ghost"
                size="icon-sm"
                aria-label="Xoá ngày lễ"
                onClick={() => holidays.remove(index)}
              >
                <IconTrash size={16} stroke={2} />
              </Button>
            </div>
            {(dateError || nameError) && (
              <p className="text-crm-sm text-crm-danger">{dateError ?? nameError}</p>
            )}
          </div>
        );
      })}
      {listError && <p className="text-crm-sm text-crm-danger">{listError}</p>}
      <Button
        type="button"
        variant="outline"
        size="sm"
        className="self-start"
        onClick={() => holidays.append({ date: "", name: "" })}
      >
        <IconPlus size={14} stroke={2} />
        Thêm ngày lễ
      </Button>
    </div>
  );
};
