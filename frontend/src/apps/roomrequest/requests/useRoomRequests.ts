import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useCurrentWorkspace } from "../../../platform/human/workspace";
import type { RequestScope } from "../../../platform/services";
import { useFallbackInterval } from "../../../platform/shared/realtime";
import {
  createRoomRequest,
  getRoomRequest,
  listRoomRequests,
} from "../services/roomRequestService";

export const useRoomRequestList = (scope: RequestScope) => {
  const { current } = useCurrentWorkspace();
  const interval = useFallbackInterval(30_000);
  return useQuery({
    queryKey: ["process", current, "room-requests", scope],
    queryFn: ({ signal }) => listRoomRequests(current!, scope, signal),
    enabled: current !== null,
    retry: false,
    refetchInterval: interval,
  });
};

/** Một yêu cầu đặt phòng của workspace đang chọn. */
export const useRoomRequest = (id: string) => {
  const { current } = useCurrentWorkspace();
  return useQuery({
    queryKey: ["roomrequest", "request", current, id],
    // `enabled` bảo đảm current khác null khi queryFn chạy
    queryFn: ({ signal }) => getRoomRequest(current!, id, signal),
    enabled: current !== null && id !== "",
  });
};

/** Tạo yêu cầu đặt phòng; trang "Yêu cầu" tự tải lại khi mở vì dữ liệu cũ đã hết hạn. */
export const useCreateRoomRequest = () => {
  const { current } = useCurrentWorkspace();
  const client = useQueryClient();
  return useMutation({
    mutationFn: (room: string) => {
      if (!current) throw new Error("Chưa chọn workspace");
      return createRoomRequest(current, room);
    },
    onSuccess: () => client.invalidateQueries({ queryKey: ["process", current] }),
  });
};
