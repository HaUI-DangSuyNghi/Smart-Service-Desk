import { useState } from "react";
import { Controller, useForm } from "react-hook-form";
import { useNavigate } from "react-router";
import { zodResolver } from "@hookform/resolvers/zod";
import { IconUpload } from "@tabler/icons-react";
import { ApiError } from "@/platform/shared/api";
import { submitOnEnter } from "@/platform/shared/lib/submitOnEnter";
import { Button } from "@/platform/shared/ui/button";
import {
  Dialog,
  DialogContent,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from "@/platform/shared/ui/dialog";
import { FileDropzone } from "@/platform/shared/ui/FileDropzone";
import { FormField } from "@/platform/shared/ui/FormField";
import { KnowledgeUploadFields } from "./KnowledgeUploadFields";
import {
  ACCEPTED_KNOWLEDGE_FILES,
  KNOWLEDGE_UPLOAD_DEFAULTS,
  knowledgeUploadSchema,
  type KnowledgeUploadInput,
  type KnowledgeUploadOutput,
} from "./knowledgeForm";
import { useCreateKnowledge } from "./useKnowledge";

// Tên file bỏ phần mở rộng, dùng làm tiêu đề gợi ý
const baseName = (file: File) => file.name.replace(/\.[^.]+$/, "");

/** Nút "Tải tài liệu lên" + form: tải file rồi tạo metadata (nguồn, phiên bản, quyền sử dụng). */
export const UploadKnowledgeDialog = () => {
  const [open, setOpen] = useState(false);
  const navigate = useNavigate();
  const create = useCreateKnowledge();
  const form = useForm<KnowledgeUploadInput, unknown, KnowledgeUploadOutput>({
    resolver: zodResolver(knowledgeUploadSchema),
    defaultValues: KNOWLEDGE_UPLOAD_DEFAULTS,
  });
  const { errors } = form.formState;

  const handleFileChange = (file: File | null, onChange: (file: File | null) => void) => {
    onChange(file);
    // Chưa nhập tiêu đề thì lấy tên file cho đỡ gõ, đã nhập rồi thì giữ nguyên
    if (file && !form.getValues("title").trim()) {
      form.setValue("title", baseName(file), { shouldValidate: true });
    }
  };

  const handleOpenChange = (next: boolean) => {
    setOpen(next);
    if (!next) {
      form.reset();
      create.reset();
    }
  };

  const handleSubmit = form.handleSubmit((values) => {
    create.mutate(values, {
      onSuccess: (created) => {
        handleOpenChange(false);
        navigate(`/knowledge/${created.id}`);
      },
    });
  });

  const submitError = create.error instanceof ApiError ? create.error : null;

  return (
    <Dialog open={open} onOpenChange={(next) => !create.isPending && handleOpenChange(next)}>
      <DialogTrigger asChild>
        <Button size="sm" className="min-h-9 md:min-h-7">
          <IconUpload size={14} stroke={2} />
          Tải tài liệu lên
        </Button>
      </DialogTrigger>

      <DialogContent className="gap-3 sm:max-w-2xl" aria-describedby={undefined}>
        <DialogHeader>
          <DialogTitle>Tải tài liệu lên</DialogTitle>
        </DialogHeader>

        <form
          id="upload-knowledge"
          onSubmit={handleSubmit}
          onKeyDown={submitOnEnter}
          className="flex flex-col gap-3"
        >
          <FormField id="file" label="Tệp tài liệu" error={errors.file?.message}>
            <Controller
              control={form.control}
              name="file"
              render={({ field }) => (
                <FileDropzone
                  compact
                  id="file"
                  value={field.value}
                  onChange={(file) => handleFileChange(file, field.onChange)}
                  accept={ACCEPTED_KNOWLEDGE_FILES}
                  hint="TXT, Markdown, PDF có chữ, Word .docx · Tối đa 5 MB"
                  invalid={errors.file !== undefined}
                  disabled={create.isPending}
                />
              )}
            />
          </FormField>
          <KnowledgeUploadFields form={form} disabled={create.isPending} />
        </form>

        {create.isError && (
          <p role="alert" className="text-crm-sm text-crm-danger">
            {submitError?.detail ?? "Không tải lên được tài liệu."}
            {submitError?.requestId && ` (mã yêu cầu: ${submitError.requestId})`}
          </p>
        )}

        <DialogFooter className="flex-row items-center justify-end py-3">
          <Button
            variant="outline"
            size="sm"
            className="min-h-9 md:min-h-7"
            disabled={create.isPending}
            onClick={() => handleOpenChange(false)}
          >
            Hủy
          </Button>
          {/* Khóa nút khi đang gửi để chặn tải lên trùng */}
          <Button
            type="submit"
            form="upload-knowledge"
            size="sm"
            className="min-h-9 md:min-h-7"
            disabled={create.isPending}
          >
            {create.isPending ? "Đang tải lên..." : "Tải lên"}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
};
