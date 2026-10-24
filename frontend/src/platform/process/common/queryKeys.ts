import type { InboxScope, RequestScope } from "@/platform/services";

const all = (workspace: string | null) => ["process", workspace] as const;

/**
 * Khóa cache của khối P. Mọi khóa dữ liệu thay đổi theo thao tác (công việc, tiến độ, yêu cầu)
 * bắt đầu bằng ["process", workspace] để nhận/hoàn thành việc xong làm mới cả khối một lần.
 */
export const processKeys = {
  all,
  inbox: (workspace: string | null, scope: InboxScope, page: number) =>
    [...all(workspace), "inbox", scope, page] as const,
  timeline: (workspace: string | null, id: string) => [...all(workspace), "timeline", id] as const,
  requests: (workspace: string | null, scope: RequestScope, page: number) =>
    [...all(workspace), "requests", scope, page] as const,
  // Danh mục quy trình gần như không đổi nên để ngoài nhánh "process", không bị làm mới theo thao tác
  catalog: (workspace: string | null) => ["process-catalog", workspace] as const,
};
