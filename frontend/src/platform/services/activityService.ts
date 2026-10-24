import { apiClient, type Page } from "@/platform/shared/api";
import { hpdiWorkspace } from "./paths";

/** Một thao tác người dùng đã làm (schema ActivityEntry trong contracts/hpdi/openapi.yaml). */
export interface ActivityEntry {
  id: string;
  occurredAt: string;
  /** Subject người thực hiện. */
  actor: string;
  /** Dạng <đối tượng>.<động từ>: process.start, task.claim, task.complete, file.upload... */
  action: string;
  resourceType: string;
  resource: string;
  /** Quy trình liên quan để mở trang tiến độ; null nếu không thuộc quy trình. */
  processInstanceId: string | null;
  processKey: string | null;
  /** Tham chiếu an toàn: taskKey, outcome, businessKey... */
  details: Record<string, string>;
}

/** GET hoạt động của tôi: thao tác do chính người gọi làm trong workspace, mới nhất trước. */
export const getMyActivity = async (
  workspace: string,
  { page = 0, size = 20 }: { page?: number; size?: number },
  signal?: AbortSignal,
): Promise<Page<ActivityEntry>> => {
  const { data } = await apiClient.get<Page<ActivityEntry>>(
    `${hpdiWorkspace(workspace)}/activity`,
    {
      params: { page, size },
      signal,
    },
  );
  return data;
};

export interface AuditFilter {
  /** ISO, tính cả. */
  from?: string;
  /** ISO, không tính. */
  to?: string;
  action?: string;
  /** Subject người thực hiện. */
  actor?: string;
  page?: number;
  size?: number;
}

/** GET nhật ký của cả workspace (điều phối viên), mới nhất trước. */
export const getWorkspaceAudit = async (
  workspace: string,
  { from, to, action, actor, page = 0, size = 20 }: AuditFilter,
  signal?: AbortSignal,
): Promise<Page<ActivityEntry>> => {
  const { data } = await apiClient.get<Page<ActivityEntry>>(`${hpdiWorkspace(workspace)}/audit`, {
    params: { from, to, action: action || undefined, actor: actor || undefined, page, size },
    signal,
  });
  return data;
};
