import { useMutation, useQuery } from "@tanstack/react-query";
import { useCurrentWorkspace } from "../../../platform/human/workspace";
import { createRoomRequest, getRoomRequest } from "../services/roomRequestService";

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
  return useMutation({
    mutationFn: (room: string) => {
      if (!current) throw new Error("Chưa chọn workspace");
      return createRoomRequest(current, room);
    },
  });
};
