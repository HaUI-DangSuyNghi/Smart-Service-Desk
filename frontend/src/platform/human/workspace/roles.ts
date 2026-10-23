import type { TagTone } from "@/platform/shared/ui/Tag";

/** Ba vai trò mặc định của workspace; Keycloak có thể thêm vai trò khác (hiện nguyên tên). */
export const ROLE_LABELS: Readonly<Record<string, string>> = {
  requester: "Người yêu cầu",
  agent: "Kỹ thuật viên",
  coordinator: "Điều phối viên",
};

export const ROLE_TONES: Readonly<Record<string, TagTone>> = {
  requester: "blue",
  agent: "green",
  coordinator: "purple",
};

export const roleLabel = (role: string) => ROLE_LABELS[role] ?? role;

export const roleTone = (role: string): TagTone => ROLE_TONES[role] ?? "gray";

/** Có ít nhất một vai trò cần thiết; không yêu cầu vai trò thì luôn đúng. */
export const hasAnyRole = (roles: readonly string[], required?: readonly string[]) => {
  return !required?.length || required.some((role) => roles.includes(role));
};
