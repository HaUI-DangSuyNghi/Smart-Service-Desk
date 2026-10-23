import { apiClient } from "@/platform/shared/api";
import { hpdiWorkspace } from "./paths";

/** Khớp schema Actor trong contracts/hpdi/openapi.yaml. */
export interface Actor {
  subject: string;
  workspace: string;
  roles: string[];
  capabilities: string[];
}

/** GET /api/hpdi/v1/workspaces/{ws}/actor: vai trò của người dùng trong một workspace. */
export const getActor = async (workspace: string, signal?: AbortSignal): Promise<Actor> => {
  const { data } = await apiClient.get<Actor>(`${hpdiWorkspace(workspace)}/actor`, { signal });
  return data;
};
