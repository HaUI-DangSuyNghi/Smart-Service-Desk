import { Client, ReconnectionTimeMode, type StompSubscription } from "@stomp/stompjs";
import { useRealtimeStore, type RealtimeStatus } from "./realtimeStore";

/** Sự kiện "có thay đổi" từ server; nhận được thì tải lại qua REST, không mang dữ liệu. */
export interface RealtimeEvent {
  type: string;
  workspace: string;
  resource: string;
  id: string | null;
  at: string;
}

export type RealtimeHandler = (event: RealtimeEvent) => void;

export interface RealtimeOptions {
  /** Lấy token mới mỗi lần nối (token hết hạn thì server đóng phiên, nối lại bằng token mới). */
  getAccessToken: () => Promise<string | null>;
  /** Nối lại sau khi mất kết nối: có thể đã lỡ sự kiện nên làm mới dữ liệu. */
  onReconnect?: () => void;
}

// Chờ trước lần nối lại: 1 giây, nhân đôi sau mỗi lần thất bại, tối đa 30 giây (nối được thì về lại 1 giây).
// stompjs chỉ đọc reconnectDelay lúc activate nên phải dùng chế độ EXPONENTIAL có sẵn, không tự đổi giữa chừng
const RECONNECT_DELAY_MS = 1000;
const MAX_RECONNECT_DELAY_MS = 30000;
const HEARTBEAT_MS = 10000;

const handlers = new Map<string, Set<RealtimeHandler>>();
const active = new Map<string, StompSubscription>();
let client: Client | null = null;
let everConnected = false;

const setStatus = (status: RealtimeStatus) => useRealtimeStore.getState().setStatus(status);

const socketUrl = () =>
  `${window.location.protocol === "https:" ? "wss" : "ws"}://${window.location.host}/ws`;

const parse = (body: string): RealtimeEvent | null => {
  try {
    const value: unknown = JSON.parse(body);
    if (typeof value !== "object" || value === null) return null;
    const event = value as Partial<RealtimeEvent>;
    return typeof event.type === "string" && typeof event.workspace === "string"
      ? (event as RealtimeEvent)
      : null;
  } catch {
    return null;
  }
};

const listen = (destination: string) => {
  if (!client?.connected || active.has(destination)) return;
  active.set(
    destination,
    client.subscribe(destination, (message) => {
      const event = parse(message.body);
      if (event) handlers.get(destination)?.forEach((handler) => handler(event));
    }),
  );
};

/** Mở kết nối dùng chung cho cả app (gọi lại khi đang mở thì bỏ qua). */
export const connectRealtime = ({ getAccessToken, onReconnect }: RealtimeOptions) => {
  if (client) return;
  everConnected = false;
  setStatus("connecting");
  const next = new Client({
    brokerURL: socketUrl(),
    heartbeatIncoming: HEARTBEAT_MS,
    heartbeatOutgoing: HEARTBEAT_MS,
    reconnectDelay: RECONNECT_DELAY_MS,
    reconnectTimeMode: ReconnectionTimeMode.EXPONENTIAL,
    maxReconnectDelay: MAX_RECONNECT_DELAY_MS,
    // Token đặt trong frame CONNECT, không đặt trên URL để khỏi lọt vào log proxy
    beforeConnect: async (stomp) => {
      const token = await getAccessToken();
      stomp.connectHeaders = token ? { Authorization: `Bearer ${token}` } : {};
    },
    onConnect: () => {
      const reconnected = everConnected;
      everConnected = true;
      active.clear();
      handlers.forEach((_, destination) => listen(destination));
      setStatus("connected");
      if (reconnected) onReconnect?.();
    },
    onWebSocketClose: () => {
      active.clear();
      if (client !== next) return;
      setStatus(everConnected ? "reconnecting" : "connecting");
    },
  });
  client = next;
  next.activate();
};

/** Đóng kết nối (đăng xuất, tab ẩn lâu); các đăng ký vẫn giữ để nối lại thì đăng ký lại. */
export const disconnectRealtime = () => {
  const current = client;
  client = null;
  active.clear();
  setStatus("off");
  void current?.deactivate();
};

/** Nghe một kênh; trả hàm huỷ. Nhiều nơi nghe cùng kênh thì dùng chung một đăng ký STOMP. */
export const subscribeRealtime = (destination: string, handler: RealtimeHandler) => {
  let set = handlers.get(destination);
  if (!set) {
    set = new Set();
    handlers.set(destination, set);
  }
  set.add(handler);
  listen(destination);
  return () => {
    set.delete(handler);
    if (set.size > 0) return;
    handlers.delete(destination);
    const subscription = active.get(destination);
    active.delete(destination);
    // Đã mất kết nối thì server đã tự bỏ đăng ký
    if (subscription && client?.connected) subscription.unsubscribe();
  };
};
