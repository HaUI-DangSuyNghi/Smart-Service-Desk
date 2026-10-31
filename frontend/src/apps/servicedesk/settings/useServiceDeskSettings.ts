import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  getServiceDeskSettings,
  updateServiceDeskSettings,
  type ServiceDeskSettings,
} from "../services/ticketService";

// Một bộ cấu hình cho mọi workspace nên khóa không có workspace
const SETTINGS_KEY = ["servicedesk", "settings"] as const;

export const useServiceDeskSettings = () =>
  useQuery({ queryKey: SETTINGS_KEY, queryFn: ({ signal }) => getServiceDeskSettings(signal) });

/** Lưu; xong hay lỗi (409: người khác vừa sửa) đều tải lại bản mới nhất. */
export const useSaveServiceDeskSettings = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (settings: ServiceDeskSettings) => updateServiceDeskSettings(settings),
    onSettled: () => queryClient.invalidateQueries({ queryKey: SETTINGS_KEY }),
  });
};
