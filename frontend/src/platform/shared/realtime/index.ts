// Realtime qua WebSocket (STOMP): server chỉ báo "có thay đổi", nơi nghe làm mới cache TanStack Query
export type { RealtimeEvent } from "./realtimeClient";
export type { RealtimeStatus } from "./realtimeStore";
export {
  USER_EVENTS,
  useFallbackInterval,
  useRealtimeConnection,
  useRealtimeEvents,
  useRealtimeInvalidation,
  useRealtimeStatus,
  workspaceTopic,
} from "./useRealtime";
