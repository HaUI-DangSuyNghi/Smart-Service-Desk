import { useWorkspaces } from "./useWorkspaces";
import { useWorkspaceStore } from "./workspaceStore";

/**
 * Workspace của người dùng và workspace đang làm việc.
 * Lựa chọn cũ không còn hợp lệ thì lấy workspace đầu tiên.
 */
export const useCurrentWorkspace = () => {
  const { data } = useWorkspaces();
  const selected = useWorkspaceStore((state) => state.selected);
  const select = useWorkspaceStore((state) => state.select);

  const workspaces = data ?? [];
  const codes = workspaces.map((item) => item.workspace);
  const current = selected && codes.includes(selected) ? selected : (codes[0] ?? null);
  const currentName = workspaces.find((item) => item.workspace === current)?.name ?? current;

  return { workspaces, current, currentName, select };
};
