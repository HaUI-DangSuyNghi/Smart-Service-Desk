import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  getServiceDeskFeatures,
  getServiceDeskSettings,
  updateServiceDeskSettings,
  type ServiceDeskSettings,
} from "../services/ticketService";

// Một bộ cấu hình cho mọi workspace nên khóa không có workspace
const SETTINGS_KEY = ["servicedesk", "settings"] as const;
const FEATURES_KEY = [...SETTINGS_KEY, "features"] as const;

export const useServiceDeskSettings = () =>
  useQuery({ queryKey: SETTINGS_KEY, queryFn: ({ signal }) => getServiceDeskSettings(signal) });

/** Cờ giao diện (dữ liệu mẫu ở form tạo ticket); mọi vai trò đọc được. */
export const useServiceDeskFeatures = () =>
  useQuery({
    queryKey: FEATURES_KEY,
    queryFn: ({ signal }) => getServiceDeskFeatures(signal),
    staleTime: 60_000,
  });

/** Lưu; xong hay lỗi (409: người khác vừa sửa) đều tải lại bản mới nhất. */
export const useSaveServiceDeskSettings = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (settings: ServiceDeskSettings) => updateServiceDeskSettings(settings),
    onSettled: () => queryClient.invalidateQueries({ queryKey: SETTINGS_KEY }),
  });
};
