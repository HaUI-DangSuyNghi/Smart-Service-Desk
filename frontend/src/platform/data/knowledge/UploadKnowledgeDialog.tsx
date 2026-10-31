import { useState } from "react";
import { Controller, useForm } from "react-hook-form";
import { useNavigate } from "react-router";
import { zodResolver } from "@hookform/resolvers/zod";
import { IconUpload } from "@tabler/icons-react";
import { z } from "zod";
import { ApiError } from "@/platform/shared/api";
import { submitOnEnter } from "@/platform/shared/lib/submitOnEnter";
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
import { FormField } from "@/platform/shared/ui/FormField";
import { Input } from "@/platform/shared/ui/input";
import { useCreateKnowledge } from "./useKnowledge";

const MAX_FILE_BYTES = 5 * 1024 * 1024;
const ACCEPTED_EXTENSIONS = [".txt", ".md", ".markdown", ".pdf", ".docx"];
const ACCEPTED_FILES = ACCEPTED_EXTENSIONS.join(",");

const hasAcceptedExtension = (file: File) =>
  ACCEPTED_EXTENSIONS.some((extension) => file.name.toLowerCase().endsWith(extension));

// Tên file bỏ phần mở rộng, dùng làm tiêu đề gợi ý
const baseName = (file: File) => file.name.replace(/\.[^.]+$/, "");

// Giới hạn độ dài khớp NewMetadata trong contracts/openapi/core.yaml.
// Kiểm đuôi file ở đây vì kéo thả không bị lọc theo accept như hộp chọn file
const schema = z.object({
  file: z
    .custom<File | null>()
    .refine((file) => file instanceof File, "Chọn một file")
    .refine(
      (file) => !file || hasAcceptedExtension(file),
      "Chỉ nhận .txt, .md, PDF có chữ hoặc Word .docx",
    )
    .refine((file) => !file || file.size <= MAX_FILE_BYTES, "File tối đa 5 MB"),
  title: z.string().trim().min(1, "Nhập tiêu đề").max(200, "Tối đa 200 ký tự"),
  source: z.string().trim().min(1, "Nhập nguồn tài liệu").max(1000, "Tối đa 1000 ký tự"),
  version: z.string().trim().min(1, "Nhập phiên bản").max(100, "Tối đa 100 ký tự"),
  usageRights: z.string().trim().min(1, "Nhập quyền sử dụng").max(200, "Tối đa 200 ký tự"),
});

// Lúc nhập file có thể còn null; sau khi zod kiểm xong (lúc gửi) chắc chắn là File
type FormInput = z.input<typeof schema>;
type FormOutput = z.output<typeof schema>;

/** Nút "Tải tài liệu lên" + form: tải file rồi tạo metadata (nguồn, phiên bản, quyền sử dụng). */
export const UploadKnowledgeDialog = () => {
  const [open, setOpen] = useState(false);
  const navigate = useNavigate();
  const create = useCreateKnowledge();
  const form = useForm<FormInput, unknown, FormOutput>({
    resolver: zodResolver(schema),
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
                  id="file"
                  value={field.value}
                  onChange={(file) => handleFileChange(file, field.onChange)}
                  accept={ACCEPTED_FILES}
                  hint="File .txt hoặc .md, UTF-8, tối đa 5 MB"
                  invalid={errors.file !== undefined}
                  disabled={create.isPending}
                />
              )}
            />
          </FormField>
          <FormField id="title" label="Tiêu đề" error={errors.title?.message}>
            <Input id="title" {...form.register("title")} />
          </FormField>
          <FormField
            id="source"
            label="Nguồn (đường dẫn hoặc tên tài liệu gốc)"
            error={errors.source?.message}
          >
            <Input id="source" {...form.register("source")} />
          </FormField>
          <FormField id="version" label="Phiên bản" error={errors.version?.message}>
            <Input id="version" {...form.register("version")} />
          </FormField>
          <FormField
            id="usageRights"
            label="Quyền sử dụng (ví dụ CC BY 4.0, nội bộ)"
            error={errors.usageRights?.message}
          >
            <Input id="usageRights" {...form.register("usageRights")} />
          </FormField>
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
