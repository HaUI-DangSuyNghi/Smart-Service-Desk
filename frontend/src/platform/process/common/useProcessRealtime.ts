import { useCurrentWorkspace } from "@/platform/human/workspace";
import { USER_EVENTS, useRealtimeInvalidation, workspaceTopic } from "@/platform/shared/realtime";
import { processKeys } from "./queryKeys";

/**
 * Đặt một lần trong khung app: việc đổi (giao, bắt đầu, xong, ca trực, sửa ước tính) thì hộp việc,
 * hàng đợi, cảnh báo tải lại. Người gửi không nghe được kênh chung nên yêu cầu của mình đổi thì báo qua kênh riêng.
 */
export const useProcessRealtime = () => {
  const { current } = useCurrentWorkspace();
  useRealtimeInvalidation(current ? workspaceTopic(current, "work") : null, () => [
    processKeys.all(current),
    processKeys.alerts(current),
  ]);
  useRealtimeInvalidation(USER_EVENTS, (event) =>
    event.type === "NOTIFICATION" ? [] : [processKeys.all(event.workspace)],
  );
};
