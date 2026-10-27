import { apiClient } from "@/platform/shared/api";
import { isMockSubject } from "./mock/mockDefaults";
import { isWorkflowMock } from "./mock/mockMode";
import { workMock } from "./mock/workMock";
import { hpdiWorkspace } from "./paths";

/** Khớp WorkspaceContext trong contracts/hpdi/openapi.yaml. */
export interface WorkspaceSummary {
  workspace: string;
  /** Tên hiển thị (Keycloak group displayName); không có thì bằng mã workspace. */
  name: string;
  roles: string[];
}

/** Khớp WorkspaceMember trong contracts/hpdi/openapi.yaml. */
export interface WorkspaceMember {
  subject: string;
  username: string;
  fullName: string | null;
  roles: string[];
}

/** Một quyền của tôi trong workspace (schema WorkspacePermission trong contracts/hpdi/openapi.yaml). */
export interface WorkspacePermission {
  /** Tên quyền, ví dụ "request:create". */
  key: string;
  /** Mô tả tiếng Việt khai báo trong Keycloak, ví dụ "Tạo yêu cầu". */
  label: string;
}

/** GET /api/hpdi/v1/workspaces: workspace của tôi kèm tên hiển thị và vai trò. */
export const getMyWorkspaces = async (signal?: AbortSignal): Promise<WorkspaceSummary[]> => {
  const { data } = await apiClient.get<WorkspaceSummary[]>("/hpdi/v1/workspaces", { signal });
  return data;
};

/** GET quyền của tôi trong workspace (từ Keycloak); chỉ để hiển thị, backend tự kiểm quyền. */
export const getMyPermissions = async (
  workspace: string,
  signal?: AbortSignal,
): Promise<WorkspacePermission[]> => {
  const { data } = await apiClient.get<WorkspacePermission[]>(
    `${hpdiWorkspace(workspace)}/permissions`,
    { signal },
  );
  return data;
};

/**
 * GET tên hiển thị của các subject đã biết (người tạo, người xử lý). Không cần quyền xem danh bạ;
 * danh bạ lỗi thì trả object rỗng.
 */
export const getPeopleNames = async (
  workspace: string,
  ids: readonly string[],
  signal?: AbortSignal,
): Promise<Record<string, string>> => {
  // Người giả (dữ liệu mẫu) tra tên tại chỗ, chỉ hỏi backend tên người thật
  const realIds = isWorkflowMock ? ids.filter((id) => !isMockSubject(id)) : ids;
  const mockNames = isWorkflowMock ? workMock.peopleNames(ids) : {};
  if (realIds.length === 0) return mockNames;
  const { data } = await apiClient.get<Record<string, string>>(
    `${hpdiWorkspace(workspace)}/people`,
    {
      params: { ids: realIds.join(",") },
      signal,
    },
  );
  return { ...data, ...mockNames };
};

/** GET /api/hpdi/v1/workspaces/{ws}/members: danh bạ workspace (503 khi Keycloak không phản hồi). */
export const getMembers = async (
  workspace: string,
  signal?: AbortSignal,
): Promise<WorkspaceMember[]> => {
  const { data } = await apiClient.get<WorkspaceMember[]>(`${hpdiWorkspace(workspace)}/members`, {
    signal,
  });
  return data;
};
