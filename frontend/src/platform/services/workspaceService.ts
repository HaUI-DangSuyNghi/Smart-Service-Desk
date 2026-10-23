import { apiClient } from "@/platform/shared/api";
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

/** GET /api/hpdi/v1/workspaces: workspace của tôi kèm tên hiển thị và vai trò. */
export const getMyWorkspaces = async (signal?: AbortSignal): Promise<WorkspaceSummary[]> => {
  const { data } = await apiClient.get<WorkspaceSummary[]>("/hpdi/v1/workspaces", { signal });
  return data;
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
