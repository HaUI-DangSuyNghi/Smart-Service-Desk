import { apiClient, type Page } from "@/platform/shared/api";
import { coreWorkspace, hpdiWorkspace } from "./paths";

/** Một dòng danh sách Knowledge Hub (schema KnowledgeSummary trong hpdi/openapi.yaml). */
export interface KnowledgeSummary {
  id: string;
  workspace: string;
  title: string;
  source: string;
  version: string;
  usageRights: string;
  owner: string;
  /** null khi danh bạ không phản hồi hoặc người tạo đã rời workspace. */
  ownerName: string | null;
  createdAt: string;
}

/** Provenance của một tài liệu (schema KnowledgeRecord trong hpdi/openapi.yaml), dùng ở trang chi tiết. */
export interface KnowledgeRecord {
  id: string;
  workspace: string;
  title: string;
  source: string;
  version: string;
  usageRights: string;
  checksum: string;
  fileId: string | null;
  owner: string;
  /** null khi danh bạ không phản hồi. */
  ownerName: string | null;
  createdAt: string;
}

/** Nội dung đã kiểm quyền và checksum (schema AuthorizedKnowledge trong hpdi/openapi.yaml). */
export interface KnowledgeContent {
  metadataId: string;
  title: string;
  version: string;
  mediaType: string;
  text: string;
}

export interface StoredFile {
  id: string;
  originalName: string;
  mediaType: string;
  size: number;
}

export interface KnowledgeQuery {
  q?: string;
  page?: number;
  size?: number;
}

export interface NewKnowledge {
  file: File;
  title: string;
  /** Tự soạn, tên đơn vị/tác giả hoặc đường dẫn tài liệu gốc. */
  source: string;
  version: string;
  /** Form mới dùng PUBLIC hoặc INTERNAL; backend vẫn nhận nhãn giấy phép cũ. */
  usageRights: string;
}

/** GET hpdi knowledge: chỉ tài liệu văn bản, mới nhất trước, kèm tên người tạo. */
export const searchKnowledge = async (
  workspace: string,
  { q, page = 0, size = 20 }: KnowledgeQuery,
  signal?: AbortSignal,
): Promise<Page<KnowledgeSummary>> => {
  const { data } = await apiClient.get<Page<KnowledgeSummary>>(
    `${hpdiWorkspace(workspace)}/knowledge`,
    {
      params: { q: q || undefined, page, size },
      signal,
    },
  );
  return data;
};

/** GET hpdi provenance một tài liệu (cần quyền xem tài liệu). */
export const getKnowledgeRecord = async (
  workspace: string,
  id: string,
  signal?: AbortSignal,
): Promise<KnowledgeRecord> => {
  const { data } = await apiClient.get<KnowledgeRecord>(
    `${hpdiWorkspace(workspace)}/knowledge/${encodeURIComponent(id)}/record`,
    { signal },
  );
  return data;
};

/** GET hpdi knowledge: chỉ trả được với file văn bản (text/plain, text/markdown). */
export const getKnowledgeContent = async (
  workspace: string,
  id: string,
  signal?: AbortSignal,
): Promise<KnowledgeContent> => {
  const { data } = await apiClient.get<KnowledgeContent>(
    `${hpdiWorkspace(workspace)}/knowledge/${encodeURIComponent(id)}`,
    { signal },
  );
  return data;
};

export const describeFile = async (workspace: string, fileId: string): Promise<StoredFile> => {
  const { data } = await apiClient.get<StoredFile>(
    `${coreWorkspace(workspace)}/files/${encodeURIComponent(fileId)}`,
  );
  return data;
};

export const downloadFile = async (workspace: string, fileId: string): Promise<Blob> => {
  const { data } = await apiClient.get<Blob>(
    `${coreWorkspace(workspace)}/files/${encodeURIComponent(fileId)}/content`,
    {
      responseType: "blob",
    },
  );
  return data;
};

/** Tải file lên rồi tạo metadata trỏ tới file đó. Chỉ agent/coordinator được tạo metadata. */
export const createKnowledge = async (
  workspace: string,
  input: NewKnowledge,
): Promise<KnowledgeRecord> => {
  const form = new FormData();
  form.append("file", withTextMediaType(input.file));
  const { data: stored } = await apiClient.post<StoredFile>(
    `${coreWorkspace(workspace)}/files`,
    form,
  );

  const { data } = await apiClient.post<KnowledgeRecord>(`${coreWorkspace(workspace)}/metadata`, {
    title: input.title,
    source: input.source,
    version: input.version,
    usageRights: input.usageRights,
    fileId: stored.id,
  });
  return data;
};

// Trình duyệt thường không gán kiểu cho file .md; thiếu kiểu thì backend không xem trước được
const withTextMediaType = (file: File): File => {
  const extension = file.name.toLowerCase().split(".").pop() ?? "";
  const types: Record<string, string> = {
    md: "text/markdown",
    markdown: "text/markdown",
    txt: "text/plain",
    pdf: "application/pdf",
    docx: "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
  };
  return types[extension] ? new File([file], file.name, { type: types[extension] }) : file;
};
