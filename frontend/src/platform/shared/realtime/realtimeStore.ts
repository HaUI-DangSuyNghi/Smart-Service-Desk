import { create } from "zustand";

/** off: chưa nối / đã tắt; connecting: lần nối đầu; reconnecting: mất kết nối, đang nối lại. */
export type RealtimeStatus = "off" | "connecting" | "connected" | "reconnecting";

interface RealtimeState {
  status: RealtimeStatus;
  setStatus: (status: RealtimeStatus) => void;
}

/** Trạng thái kết nối WebSocket, để các hook chọn chu kỳ polling dự phòng. */
export const useRealtimeStore = create<RealtimeState>((set) => ({
  status: "off",
  setStatus: (status) => set({ status }),
}));
