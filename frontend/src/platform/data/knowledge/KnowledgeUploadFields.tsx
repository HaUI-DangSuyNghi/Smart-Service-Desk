import type { UseFormReturn } from "react-hook-form";
import { IconLock, IconWorld } from "@tabler/icons-react";
import { ChoiceCard } from "@/platform/shared/ui/ChoiceCard";
import { FormField } from "@/platform/shared/ui/FormField";
import { Input } from "@/platform/shared/ui/input";
import type { KnowledgeUploadInput, KnowledgeUploadOutput } from "./knowledgeForm";

interface Props {
  form: UseFormReturn<KnowledgeUploadInput, unknown, KnowledgeUploadOutput>;
  disabled: boolean;
}

export const KnowledgeUploadFields = ({ form, disabled }: Props) => {
  const { errors } = form.formState;
  return (
    <fieldset disabled={disabled} className="flex min-w-0 flex-col gap-3">
      <div className="grid min-w-0 grid-cols-1 gap-3 sm:grid-cols-[minmax(0,1fr)_7rem]">
        <FormField id="title" label="Tiêu đề tài liệu" error={errors.title?.message}>
          <Input id="title" maxLength={200} {...form.register("title")} />
        </FormField>
        <FormField id="version" label="Phiên bản" error={errors.version?.message}>
          <Input id="version" maxLength={100} {...form.register("version")} />
        </FormField>
      </div>
      <FormField
        id="source"
        label="Nguồn tài liệu"
        hint="Tên đơn vị, tác giả hoặc đường dẫn tài liệu gốc. Nếu tự biên soạn, nhập Tự soạn."
        error={errors.source?.message}
      >
        <Input
          id="source"
          placeholder="VD: Tự soạn, Phòng CNTT hoặc https://…"
          maxLength={1000}
          {...form.register("source")}
        />
      </FormField>
      <fieldset className="flex min-w-0 flex-col gap-2">
        <legend className="mb-2 text-crm-sm text-crm-text-2">Ai được xem?</legend>
        <div className="grid grid-cols-2 items-stretch gap-2">
          <ChoiceCard value="PUBLIC" {...form.register("usageRights")}>
            <span className="flex items-center gap-2 font-medium">
              <IconWorld size={16} />
              Công khai
            </span>
            <span className="mt-1 block text-crm-xs text-crm-text-2">Thành viên workspace</span>
          </ChoiceCard>
          <ChoiceCard value="INTERNAL" {...form.register("usageRights")}>
            <span className="flex items-center gap-2 font-medium">
              <IconLock size={16} />
              Nội bộ
            </span>
            <span className="mt-1 block text-crm-xs text-crm-text-2">
              Kỹ thuật viên, điều phối, admin
            </span>
          </ChoiceCard>
        </div>
      </fieldset>
    </fieldset>
  );
};
