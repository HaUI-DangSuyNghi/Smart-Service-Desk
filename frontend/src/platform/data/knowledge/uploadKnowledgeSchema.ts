import { z } from "zod";

export const MAX_KNOWLEDGE_FILE_BYTES = 5 * 1024 * 1024;

const extensions = [".txt", ".md", ".markdown"];
export const ACCEPTED_KNOWLEDGE_FILES = extensions.join(",");

export const uploadKnowledgeSchema = z.object({
  file: z
    .custom<File | null>()
    .refine((file): file is File => file instanceof File, "Chọn một file")
    .refine(
      (file) => !file || extensions.some((extension) => file.name.toLowerCase().endsWith(extension)),
      "Chỉ nhận file .txt hoặc .md",
    )
    .refine((file) => !file || file.size <= MAX_KNOWLEDGE_FILE_BYTES, "File tối đa 5 MB"),
  title: z.string().trim().min(1, "Nhập tiêu đề").max(200, "Tối đa 200 ký tự"),
  source: z.string().trim().min(1, "Nhập nguồn tài liệu").max(1000, "Tối đa 1000 ký tự"),
  version: z.string().trim().min(1, "Nhập phiên bản").max(100, "Tối đa 100 ký tự"),
  usageRights: z.string().trim().min(1, "Nhập quyền sử dụng").max(200, "Tối đa 200 ký tự"),
});

export type KnowledgeFormInput = z.input<typeof uploadKnowledgeSchema>;
export type KnowledgeFormOutput = z.output<typeof uploadKnowledgeSchema>;
