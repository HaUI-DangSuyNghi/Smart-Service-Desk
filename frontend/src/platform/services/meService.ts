import { apiClient } from "@/platform/shared/api";

/** Khớp schema Me trong contracts/openapi/core.yaml. */
export interface Me {
  sub: string;
  username: string;
  memberships: Membership[];
  platformAdmin: boolean;
}

export interface Membership {
  workspace: string;
  role: string;
}

/** GET /api/core/v1/me: người dùng hiện tại, workspace và vai trò. */
export const getMe = async (signal?: AbortSignal): Promise<Me> => {
  const { data } = await apiClient.get<Me>("/core/v1/me", { signal });
  return data;
};
