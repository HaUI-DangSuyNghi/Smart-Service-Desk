import { useState, type ReactNode } from "react";
import { Controller, useForm } from "react-hook-form";
import { useNavigate } from "react-router";
import { zodResolver } from "@hookform/resolvers/zod";
import { IconUpload } from "@tabler/icons-react";
import { ApiError } from "@/platform/shared/api";
import { Button } from "@/platform/shared/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from "@/platform/shared/ui/dialog";
import { FileDropzone } from "@/platform/shared/ui/FileDropzone";
import { Input } from "@/platform/shared/ui/input";
import { Label } from "@/platform/shared/ui/label";
import { useCreateKnowledge } from "./useKnowledge";
import {
  ACCEPTED_KNOWLEDGE_FILES,
  uploadKnowledgeSchema,
  type KnowledgeFormInput,
  type KnowledgeFormOutput,
} from "./uploadKnowledgeSchema";

// Tên file bỏ phần mở rộng, dùng làm tiêu đề gợi ý
const baseName = (file: File) => file.name.replace(/\.[^.]+$/, "");

interface FieldProps {
  id: string;
  label: string;
  error?: string;
  children: ReactNode;
}

const Field = ({ id, label, error, children }: FieldProps) => (
  <div className="flex flex-col gap-1">
    <Label htmlFor={id} className="text-crm-sm font-normal text-crm-text-2">
      {label}
    </Label>
    {children}
    {error && <p className="text-crm-sm text-crm-danger">{error}</p>}
  </div>
);

/** Nút "Tải tài liệu lên" + form: tải file rồi tạo metadata (nguồn, phiên bản, quyền sử dụng). */
export const UploadKnowledgeDialog = () => {
  const [open, setOpen] = useState(false);
  const navigate = useNavigate();
  const create = useCreateKnowledge();
  const form = useForm<KnowledgeFormInput, unknown, KnowledgeFormOutput>({
    resolver: zodResolver(uploadKnowledgeSchema),
    defaultValues: { file: null, title: "", source: "", version: "1.0", usageRights: "" },
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

  const handleSubmit = form.handleSubmit(({ file, ...fields }) => {
    // schema đã kiểm có file
    create.mutate(
      { file, ...fields },
      {
        onSuccess: (created) => {
          handleOpenChange(false);
          navigate(`/knowledge/${created.id}`);
        },
      },
    );
  });

  const submitError = create.error instanceof ApiError ? create.error : null;

  return (
    <Dialog open={open} onOpenChange={handleOpenChange}>
      <DialogTrigger asChild>
        <Button size="sm">
          <IconUpload size={14} stroke={2} />
          Tải tài liệu lên
        </Button>
      </DialogTrigger>

      <DialogContent className="sm:max-w-100">
        <DialogHeader>
          <DialogTitle>Tải tài liệu lên</DialogTitle>
          <DialogDescription>
            Tài liệu văn bản .txt hoặc .md (UTF-8, tối đa 5 MB). Nội dung xem trước được và dùng cho
            gợi ý AI.
          </DialogDescription>
        </DialogHeader>

        <form id="upload-knowledge" onSubmit={handleSubmit} className="flex flex-col gap-3">
          <Field id="file" label="Tệp tài liệu" error={errors.file?.message}>
            <Controller
              control={form.control}
              name="file"
              render={({ field }) => (
                <FileDropzone
                  id="file"
                  value={field.value}
                  onChange={(file) => handleFileChange(file, field.onChange)}
                  accept={ACCEPTED_KNOWLEDGE_FILES}
                  hint="File .txt hoặc .md, UTF-8, tối đa 5 MB"
                  invalid={errors.file !== undefined}
                  disabled={create.isPending}
                />
              )}
            />
          </Field>
          <Field id="title" label="Tiêu đề" error={errors.title?.message}>
            <Input id="title" {...form.register("title")} />
          </Field>
          <Field
            id="source"
            label="Nguồn (đường dẫn hoặc tên tài liệu gốc)"
            error={errors.source?.message}
          >
            <Input id="source" {...form.register("source")} />
          </Field>
          <Field id="version" label="Phiên bản" error={errors.version?.message}>
            <Input id="version" {...form.register("version")} />
          </Field>
          <Field
            id="usageRights"
            label="Quyền sử dụng (ví dụ CC BY 4.0, nội bộ)"
            error={errors.usageRights?.message}
          >
            <Input id="usageRights" {...form.register("usageRights")} />
          </Field>
        </form>

        {create.isError && (
          <p role="alert" className="text-crm-sm text-crm-danger">
            {submitError?.detail ?? "Không tải lên được tài liệu."}
            {submitError?.requestId && ` (mã yêu cầu: ${submitError.requestId})`}
          </p>
        )}

        <DialogFooter>
          <Button variant="outline" size="sm" onClick={() => handleOpenChange(false)}>
            Hủy
          </Button>
          {/* Khóa nút khi đang gửi để chặn tải lên trùng */}
          <Button type="submit" form="upload-knowledge" size="sm" disabled={create.isPending}>
            {create.isPending ? "Đang tải lên..." : "Tải lên"}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
};
