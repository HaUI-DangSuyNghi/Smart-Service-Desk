import { useMe } from "./useMe";
import { useWorkspaceStore } from "./workspaceStore";

/**
 * Danh sách workspace của người dùng (từ /me) và workspace đang làm việc.
 * Lựa chọn cũ không còn hợp lệ thì lấy workspace đầu tiên.
 */
export const useCurrentWorkspace = () => {
  const { data: me } = useMe();
  const selected = useWorkspaceStore((state) => state.selected);
  const select = useWorkspaceStore((state) => state.select);

  const workspaces = [...new Set(me?.memberships.map((m) => m.workspace) ?? [])].sort();
  const current = selected && workspaces.includes(selected) ? selected : (workspaces[0] ?? null);

  return { workspaces, current, select };
};
