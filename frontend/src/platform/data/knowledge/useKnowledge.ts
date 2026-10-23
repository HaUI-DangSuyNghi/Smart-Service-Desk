import { keepPreviousData, useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useCurrentWorkspace } from "@/platform/human/workspace";
import {
  createKnowledge,
  describeFile,
  downloadFile,
  getKnowledgeContent,
  getKnowledgeRecord,
  searchKnowledge,
  type NewKnowledge,
} from "@/platform/services";
import { saveBlob } from "@/platform/shared/lib/saveBlob";

export const KNOWLEDGE_PAGE_SIZE = 20;

/** Vai trò được tải tài liệu lên (backend: tạo metadata cần agent hoặc coordinator). */
export const KNOWLEDGE_EDITOR_ROLES = ["agent", "coordinator"] as const;

export const useKnowledgeList = (q: string, page: number) => {
  const { current } = useCurrentWorkspace();
  return useQuery({
    queryKey: ["knowledge", current, "list", q, page],
    // `enabled` bảo đảm current khác null khi queryFn chạy
    queryFn: ({ signal }) =>
      searchKnowledge(current!, { q, page, size: KNOWLEDGE_PAGE_SIZE }, signal),
    enabled: current !== null,
    // Đổi trang/tìm kiếm vẫn giữ bảng cũ, tránh chớp trắng
    placeholderData: keepPreviousData,
  });
};

export const useKnowledgeRecord = (id: string) => {
  const { current } = useCurrentWorkspace();
  return useQuery({
    queryKey: ["knowledge", current, "record", id],
    queryFn: ({ signal }) => getKnowledgeRecord(current!, id, signal),
    enabled: current !== null,
  });
};

/** Nội dung văn bản; chỉ gọi khi bản ghi có file. */
export const useKnowledgeContent = (id: string, hasFile: boolean) => {
  const { current } = useCurrentWorkspace();
  return useQuery({
    queryKey: ["knowledge", current, "content", id],
    queryFn: ({ signal }) => getKnowledgeContent(current!, id, signal),
    enabled: current !== null && hasFile,
  });
};

export const useCreateKnowledge = () => {
  const { current } = useCurrentWorkspace();
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (input: NewKnowledge) => {
      if (!current) throw new Error("Chưa chọn workspace");
      return createKnowledge(current, input);
    },
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["knowledge", current] }),
  });
};

/** Tải file gốc về máy, giữ tên file lúc tải lên. */
export const useDownloadKnowledgeFile = () => {
  const { current } = useCurrentWorkspace();
  return useMutation({
    mutationFn: async (fileId: string) => {
      if (!current) throw new Error("Chưa chọn workspace");
      const [file, blob] = await Promise.all([
        describeFile(current, fileId),
        downloadFile(current, fileId),
      ]);
      saveBlob(blob, file.originalName);
    },
  });
};
