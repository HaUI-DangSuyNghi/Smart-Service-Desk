import { useEffect, useRef } from "react";
import { useQueryClient, type QueryKey } from "@tanstack/react-query";
import { createKeyBatcher } from "./keyBatcher";
import {
  connectRealtime,
  disconnectRealtime,
  subscribeRealtime,
  type RealtimeEvent,
  type RealtimeOptions,
} from "./realtimeClient";
import { useRealtimeStore } from "./realtimeStore";

const BATCH_MS = 500;
// Đang nối thì polling chỉ để dự phòng
const CONNECTED_POLL_MS = 5 * 60 * 1000;
// Tab ẩn lâu thì ngắt để đỡ giữ kết nối; hiện lại thì nối và làm mới
const HIDDEN_DISCONNECT_MS = 5 * 60 * 1000;

/** Kênh riêng của người đang đăng nhập. */
export const USER_EVENTS = "/user/queue/events";

/** Kênh chung theo workspace: /topic/workspaces/{workspace}/{channel}. */
export const workspaceTopic = (workspace: string, channel: string) =>
  `/topic/workspaces/${encodeURIComponent(workspace)}/${channel}`;

export const useRealtimeStatus = () => useRealtimeStore((state) => state.status);

/** Chu kỳ polling dự phòng: đang nối thì giãn ra 5 phút, mất kết nối thì về chu kỳ cũ. */
export const useFallbackInterval = (intervalMs: number) =>
  useRealtimeStore((state) =>
    state.status === "connected" ? Math.max(intervalMs, CONNECTED_POLL_MS) : intervalMs,
  );

/**
 * Đặt một lần trong khung app sau đăng nhập: mở kết nối, ngắt khi rời khung app;
 * tab ẩn quá 5 phút thì ngắt, hiện lại thì nối lại và làm mới dữ liệu.
 */
export const useRealtimeConnection = ({ getAccessToken, onReconnect }: RealtimeOptions) => {
  const options = useRef({ getAccessToken, onReconnect });
  useEffect(() => {
    options.current = { getAccessToken, onReconnect };
  });

  useEffect(() => {
    const open = () =>
      connectRealtime({
        getAccessToken: () => options.current.getAccessToken(),
        onReconnect: () => options.current.onReconnect?.(),
      });
    let hiddenTimer: ReturnType<typeof setTimeout> | undefined;
    let closedWhileHidden = false;
    const onVisibility = () => {
      if (document.hidden) {
        hiddenTimer = setTimeout(() => {
          closedWhileHidden = true;
          disconnectRealtime();
        }, HIDDEN_DISCONNECT_MS);
        return;
      }
      clearTimeout(hiddenTimer);
      if (!closedWhileHidden) return;
      closedWhileHidden = false;
      open();
      options.current.onReconnect?.();
    };
    open();
    document.addEventListener("visibilitychange", onVisibility);
    return () => {
      document.removeEventListener("visibilitychange", onVisibility);
      clearTimeout(hiddenTimer);
      disconnectRealtime();
    };
  }, []);
};

/** Nghe một kênh khi đang hiện; destination null = không nghe. */
export const useRealtimeEvents = (
  destination: string | null,
  onEvent: (event: RealtimeEvent) => void,
) => {
  const handler = useRef(onEvent);
  useEffect(() => {
    handler.current = onEvent;
  });
  useEffect(() => {
    if (!destination) return;
    return subscribeRealtime(destination, (event) => handler.current(event));
  }, [destination]);
};

/** Sự kiện trên kênh → khóa cache cần làm mới (gộp trong 500 ms). */
export const useRealtimeInvalidation = (
  destination: string | null,
  keysOf: (event: RealtimeEvent) => readonly QueryKey[],
) => {
  const queryClient = useQueryClient();
  const batcher = useRef<ReturnType<typeof createKeyBatcher> | null>(null);
  useEffect(() => {
    const created = createKeyBatcher(
      (keys) => keys.forEach((queryKey) => void queryClient.invalidateQueries({ queryKey })),
      BATCH_MS,
    );
    batcher.current = created;
    return () => created.cancel();
  }, [queryClient]);
  useRealtimeEvents(destination, (event) => batcher.current?.add(keysOf(event)));
};
