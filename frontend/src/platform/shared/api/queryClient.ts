import { QueryClient } from "@tanstack/react-query";
import { ApiError } from "./ApiError";

const MAX_RETRIES = 2;

export const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      // Lỗi 4xx (thiếu quyền, không tồn tại...) gọi lại cũng vậy, chỉ thử lại lỗi mạng/5xx
      retry: (failureCount, error) => {
        if (error instanceof ApiError && error.status >= 400 && error.status < 500) return false;
        return failureCount < MAX_RETRIES;
      },
      refetchOnWindowFocus: false,
    },
  },
});
