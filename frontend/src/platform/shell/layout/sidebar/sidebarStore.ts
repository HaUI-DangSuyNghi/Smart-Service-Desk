import { create } from "zustand";
import { persist } from "zustand/middleware";

interface SidebarState {
  collapsed: boolean;
  toggle: () => void;
}

/** Sidebar thu gọn hay mở; nhớ qua lần mở app sau. */
export const useSidebarStore = create<SidebarState>()(
  persist(
    (set) => ({
      collapsed: false,
      toggle: () => set((state) => ({ collapsed: !state.collapsed })),
    }),
    { name: "crm.sidebar" },
  ),
);
