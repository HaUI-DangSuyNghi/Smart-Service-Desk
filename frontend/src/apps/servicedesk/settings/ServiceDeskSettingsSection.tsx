import { IconClockHour4, IconUsersGroup } from "@tabler/icons-react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { PRIORITY_LABELS } from "../../../platform/process";
import { ApiError } from "../../../platform/shared/api";
import { Button } from "../../../platform/shared/ui/button";
import { ErrorState } from "../../../platform/shared/ui/ErrorState";
import { FieldGroup } from "../../../platform/shared/ui/FieldGroup";
import { Input } from "../../../platform/shared/ui/input";
import { FieldsSkeleton } from "../../../platform/shared/ui/Skeleton";
import { toast, toastError } from "../../../platform/shared/ui/toast";
import type { ServiceDeskSettings } from "../services/ticketService";
import { CATEGORY_LABELS } from "../tickets/ticketLabels";
import { CATEGORIES, MAX_ESTIMATE_MINUTES, PRIORITIES } from "../tickets/ticketRules";
import { useSaveServiceDeskSettings, useServiceDeskSettings } from "./useServiceDeskSettings";

const minutes = z.coerce
  .number({ message: "Nhập số" })
  .int("Số nguyên")
  .min(1, "≥ 1")
  .max(MAX_ESTIMATE_MINUTES, `≤ ${MAX_ESTIMATE_MINUTES}`);
const row = z.object({ LOW: minutes, MEDIUM: minutes, HIGH: minutes, CRITICAL: minutes });
// Tên group Keycloak (khớp backend): bắt đầu bằng chữ thường, rồi chữ thường, số, gạch ngang;
// không trùng tên vai trò
const RESERVED_GROUPS = ["requester", "agent", "coordinator", "platform-admin", "system", "global"];
const group = z
  .string()
  .trim()
  .regex(/^[a-z][a-z0-9-]{0,49}$/, "Bắt đầu bằng chữ thường; chỉ chữ thường, số, gạch ngang")
  .refine((value) => !RESERVED_GROUPS.includes(value), "Trùng tên vai trò, chọn tên khác");

const schema = z.object({
  version: z.number(),
  categoryGroups: z.object({
    HARDWARE: group,
    NETWORK: group,
    SOFTWARE: group,
    ACCOUNT: group,
    // Loại Khác luôn do điều phối viên tự chọn người (backend chỉ nhận null)
    OTHER: z.null(),
  }),
  estimateDefaults: z.object({
    HARDWARE: row,
    NETWORK: row,
    SOFTWARE: row,
    ACCOUNT: row,
    OTHER: row,
  }),
});

type FormInput = z.input<typeof schema>;
type FormValues = z.output<typeof schema>;

const SettingsForm = ({ settings }: { settings: ServiceDeskSettings }) => {
  const save = useSaveServiceDeskSettings();
  const form = useForm<FormInput, unknown, FormValues>({
    resolver: zodResolver(schema),
    defaultValues: {
      ...settings,
      categoryGroups: {
        HARDWARE: settings.categoryGroups.HARDWARE ?? "",
        NETWORK: settings.categoryGroups.NETWORK ?? "",
        SOFTWARE: settings.categoryGroups.SOFTWARE ?? "",
        ACCOUNT: settings.categoryGroups.ACCOUNT ?? "",
        OTHER: null,
      },
    },
  });
  const { errors } = form.formState;

  const handleSubmit = form.handleSubmit((values) => {
    save.mutate(values, {
      onSuccess: () => toast.success("Đã lưu cấu hình Service Desk"),
      onError: (error) =>
        toastError(
          error instanceof ApiError && error.status === 409
            ? "Người khác vừa sửa cấu hình. Đã tải bản mới, hãy sửa lại."
            : "Không lưu được cấu hình.",
          error,
        ),
    });
  });

  return (
    <form onSubmit={handleSubmit} className="flex flex-col gap-6">
      <p className="text-crm-sm text-crm-text-2">
        Lưu xong áp dụng ngay cho lần duyệt và giao việc sau đó; ticket đã duyệt giữ ước tính cũ.
      </p>
      <FieldGroup title="Tổ xử lý theo loại sự cố" icon={IconUsersGroup}>
        <p className="text-crm-sm text-crm-text-3">
          Tên tổ là group con trong workspace ở Keycloak (cùng cấp với agent). Chỉ kỹ thuật viên
          thuộc tổ mới được đề xuất và giao việc loại đó.
        </p>
        {CATEGORIES.map((category) => (
          <div
            key={category}
            className="grid grid-cols-1 gap-1 sm:grid-cols-[8rem_1fr] sm:items-center"
          >
            <label htmlFor={`group-${category}`} className="text-crm-text-2">
              {CATEGORY_LABELS[category]}
            </label>
            <div className="flex flex-col gap-1">
              {category === "OTHER" ? (
                <Input
                  id="group-OTHER"
                  className="w-full sm:w-56"
                  value="Điều phối viên tự chọn"
                  disabled
                  readOnly
                />
              ) : (
                <Input
                  id={`group-${category}`}
                  className="w-full sm:w-56"
                  {...form.register(`categoryGroups.${category}`)}
                />
              )}
              {category !== "OTHER" && errors.categoryGroups?.[category] && (
                <p className="text-crm-sm text-crm-danger">
                  {errors.categoryGroups[category]?.message}
                </p>
              )}
            </div>
          </div>
        ))}
      </FieldGroup>

      <FieldGroup title="Ước tính mặc định (phút)" icon={IconClockHour4}>
        <p className="text-crm-sm text-crm-text-3">
          Dùng khi chưa đủ việc đã đóng cùng loại và ưu tiên để tính trung vị (số mẫu tối thiểu ở
          phần Cấu hình chung).
        </p>
        {/* Bảng 5 × 4 ô: điện thoại cuộn ngang trong khung, trang không cuộn ngang */}
        <div className="overflow-x-auto">
          <table className="min-w-full border-separate border-spacing-1">
            <thead>
              <tr>
                <th className="text-left font-normal text-crm-text-3">Loại</th>
                {PRIORITIES.map((priority) => (
                  <th key={priority} className="text-left font-normal text-crm-text-3">
                    {PRIORITY_LABELS[priority]}
                  </th>
                ))}
              </tr>
            </thead>
            <tbody>
              {CATEGORIES.map((category) => (
                <tr key={category}>
                  <td className="pr-2 whitespace-nowrap text-crm-text-2">
                    {CATEGORY_LABELS[category]}
                  </td>
                  {PRIORITIES.map((priority) => (
                    <td key={priority}>
                      <Input
                        type="number"
                        inputMode="numeric"
                        aria-label={`${CATEGORY_LABELS[category]} – ${PRIORITY_LABELS[priority]}`}
                        aria-invalid={Boolean(errors.estimateDefaults?.[category]?.[priority])}
                        className="w-20"
                        {...form.register(`estimateDefaults.${category}.${priority}`)}
                      />
                    </td>
                  ))}
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        {errors.estimateDefaults && (
          <p className="text-crm-sm text-crm-danger">Ô đỏ chưa hợp lệ: số nguyên từ 1 phút.</p>
        )}
      </FieldGroup>

      <div className="flex justify-end">
        <Button type="submit" size="sm" disabled={save.isPending || !form.formState.isDirty}>
          {save.isPending ? "Đang lưu..." : "Lưu cấu hình Service Desk"}
        </Button>
      </div>
    </form>
  );
};

/** Cấu hình riêng của Service Desk trên màn Cấu hình hệ thống (mục 6). */
export const ServiceDeskSettingsSection = () => {
  const settings = useServiceDeskSettings();

  if (settings.isPending) return <FieldsSkeleton rows={6} label="Đang tải cấu hình" />;
  if (settings.error)
    return <ErrorState error={settings.error} fallback="Không tải được cấu hình." />;
  // key theo version: lưu xong thì form lấy giá trị mới, không reset bằng effect
  return <SettingsForm key={settings.data.version} settings={settings.data} />;
};
