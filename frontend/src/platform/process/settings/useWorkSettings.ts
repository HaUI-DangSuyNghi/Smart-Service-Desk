import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { getWorkSettings, updateWorkSettings, type WorkSettings } from "@/platform/services";

// Cấu hình chung cho mọi workspace nên khóa không có workspace
const SETTINGS_KEY = ["work-settings"] as const;

export const useWorkSettings = () =>
  useQuery({ queryKey: SETTINGS_KEY, queryFn: ({ signal }) => getWorkSettings(signal) });

/** Lưu cấu hình; xong hay lỗi (409: người khác vừa sửa) đều tải lại bản mới nhất. */
export const useSaveWorkSettings = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (settings: WorkSettings) => updateWorkSettings(settings),
    onSettled: () => queryClient.invalidateQueries({ queryKey: SETTINGS_KEY }),
  });
};
