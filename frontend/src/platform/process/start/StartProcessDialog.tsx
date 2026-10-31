import { useState } from "react";
import { useForm, useWatch } from "react-hook-form";
import { useNavigate } from "react-router";
import { zodResolver } from "@hookform/resolvers/zod";
import { IconPlus } from "@tabler/icons-react";
import { z } from "zod";
import { PERMISSIONS, usePermissions } from "@/platform/human/workspace";
import { submitOnEnter } from "@/platform/shared/lib/submitOnEnter";
import { Button } from "@/platform/shared/ui/button";
import { ChoiceCard } from "@/platform/shared/ui/ChoiceCard";
import {
  Dialog,
  DialogContent,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from "@/platform/shared/ui/dialog";
import { FormField } from "@/platform/shared/ui/FormField";
import { Input } from "@/platform/shared/ui/input";
import { Textarea } from "@/platform/shared/ui/textarea";
import { toast, toastError } from "@/platform/shared/ui/toast";
import { useProcessCatalog } from "../catalog/useProcessCatalog";
import { useStartProcess } from "./useStartProcess";

// Ô một dòng ≤ 200 ký tự, ô nhiều dòng ≤ 2000 (backend kiểm cùng luật); maxLength của từng ô chặn gõ quá
const SHORT_TEXT = 200;
const LONG_TEXT = 2000;
const schema = z.object({
  processKey: z.string().min(1, "Chọn quy trình"),
  variables: z.record(
    z.string().trim().min(1, "Không được để trống").max(LONG_TEXT, `Tối đa ${LONG_TEXT} ký tự`),
  ),
});

type FormValues = z.infer<typeof schema>;

/**
 * Nút "Tạo yêu cầu" + form tạo yêu cầu theo quy trình tự phục vụ (canStart).
 * Không có quy trình nào tạo được thì không hiện gì.
 */
export const StartProcessDialog = () => {
  const [open, setOpen] = useState(false);
  const navigate = useNavigate();
  const catalog = useProcessCatalog();
  const start = useStartProcess();
  const { can } = usePermissions();
  // canStart: vai trò khởi tạo theo từng quy trình (backend); request:create: quyền chung trong Keycloak
  const startable = can(PERMISSIONS.requestCreate)
    ? (catalog.data ?? []).filter((process) => process.canStart)
    : [];

  const form = useForm<FormValues>({
    resolver: zodResolver(schema),
    defaultValues: { processKey: "", variables: {} },
    // Đổi quy trình thì ô của quy trình cũ bị gỡ khỏi form, không gửi biến thừa (backend từ chối)
    shouldUnregister: true,
  });
  const selectedKey = useWatch({ control: form.control, name: "processKey" });
  const selected = startable.find((process) => process.processKey === selectedKey);
  const { errors } = form.formState;

  if (startable.length === 0) return null;

  const handleOpenChange = (next: boolean) => {
    setOpen(next);
    // Chỉ có một quy trình thì chọn sẵn
    const onlyProcess = startable.length === 1 ? startable[0]?.processKey : undefined;
    form.reset({ processKey: onlyProcess ?? "", variables: {} });
    start.reset();
  };

  const handleSubmit = form.handleSubmit((values) => {
    start.mutate(values, {
      onSuccess: (handle) => {
        toast.success("Đã tạo yêu cầu", { description: selected?.name });
        handleOpenChange(false);
        navigate(`/processes/${handle.processInstanceId}`);
      },
      onError: (error) => toastError("Không tạo được yêu cầu.", error),
    });
  });

  return (
    <Dialog open={open} onOpenChange={handleOpenChange}>
      <DialogTrigger asChild>
        <Button size="sm" aria-label="Tạo yêu cầu" className="min-h-9 md:min-h-7">
          <IconPlus size={14} stroke={2} />
          <span className="hidden sm:inline">Tạo yêu cầu</span>
        </Button>
      </DialogTrigger>

      <DialogContent className="sm:max-w-100" aria-describedby={undefined}>
        <DialogHeader>
          <DialogTitle>Tạo yêu cầu</DialogTitle>
        </DialogHeader>

        <form
          id="start-process"
          onSubmit={handleSubmit}
          onKeyDown={submitOnEnter}
          className="flex flex-col gap-3"
        >
          <fieldset className="flex flex-col gap-1.5">
            <legend className="mb-1 text-crm-sm text-crm-text-2">Quy trình</legend>
            {startable.map((process) => (
              <ChoiceCard
                key={process.processKey}
                value={process.processKey}
                {...form.register("processKey")}
              >
                {process.name}
              </ChoiceCard>
            ))}
            {errors.processKey && (
              <p className="text-crm-sm text-crm-danger">{errors.processKey.message}</p>
            )}
          </fieldset>

          {selected?.startFields.map((field) => (
            <FormField
              key={field.key}
              id={`variable-${field.key}`}
              label={field.label}
              error={errors.variables?.[field.key]?.message}
            >
              {field.multiline ? (
                <Textarea
                  id={`variable-${field.key}`}
                  rows={5}
                  maxLength={LONG_TEXT}
                  {...form.register(`variables.${field.key}`)}
                />
              ) : (
                <Input
                  id={`variable-${field.key}`}
                  maxLength={SHORT_TEXT}
                  {...form.register(`variables.${field.key}`)}
                />
              )}
            </FormField>
          ))}
        </form>

        <DialogFooter>
          <Button variant="outline" size="sm" onClick={() => handleOpenChange(false)}>
            Hủy
          </Button>
          {/* Khóa nút khi đang gửi để chặn tạo trùng */}
          <Button type="submit" form="start-process" size="sm" disabled={start.isPending}>
            {start.isPending ? "Đang tạo..." : "Tạo yêu cầu"}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
};
