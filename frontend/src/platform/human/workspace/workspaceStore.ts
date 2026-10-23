import { create } from "zustand";
import { persist } from "zustand/middleware";

interface WorkspaceState {
  /** Workspace người dùng chọn lần trước; null = chưa chọn. Không phải dữ liệu bí mật. */
  selected: string | null;
  select: (workspace: string) => void;
}

export const useWorkspaceStore = create<WorkspaceState>()(
  persist(
    (set) => ({
      selected: null,
      select: (workspace) => set({ selected: workspace }),
    }),
    { name: "crm.workspace" },
  ),
);
