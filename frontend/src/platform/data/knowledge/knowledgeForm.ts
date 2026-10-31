import { z } from "zod";

const MAX_FILE_BYTES = 5 * 1024 * 1024;
const ACCEPTED_EXTENSIONS = [".txt", ".md", ".markdown", ".pdf", ".docx"];
export const ACCEPTED_KNOWLEDGE_FILES = ACCEPTED_EXTENSIONS.join(",");

export const knowledgeUploadSchema = z.object({
  file: z
    .custom<File | null>()
    .refine((file) => file instanceof File, "Chọn một file")
    .refine(
      (file) =>
        !file ||
        ACCEPTED_EXTENSIONS.some((extension) => file.name.toLowerCase().endsWith(extension)),
      "Chỉ nhận .txt, .md, PDF có chữ hoặc Word .docx",
    )
    .refine((file) => !file || file.size <= MAX_FILE_BYTES, "File tối đa 5 MB"),
  title: z.string().trim().min(1, "Nhập tiêu đề").max(200, "Tối đa 200 ký tự"),
  source: z.string().trim().min(1, "Nhập nguồn tài liệu").max(1000, "Tối đa 1000 ký tự"),
  version: z.string().trim().min(1, "Nhập phiên bản").max(100, "Tối đa 100 ký tự"),
  usageRights: z.enum(["PUBLIC", "INTERNAL"]),
});

export type KnowledgeUploadInput = z.input<typeof knowledgeUploadSchema>;
export type KnowledgeUploadOutput = z.output<typeof knowledgeUploadSchema>;

export const KNOWLEDGE_UPLOAD_DEFAULTS: KnowledgeUploadInput = {
  file: null,
  title: "",
  source: "",
  version: "1.0",
  usageRights: "PUBLIC",
};
