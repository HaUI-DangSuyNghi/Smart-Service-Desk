import { useQuery } from "@tanstack/react-query";
import { getPeopleNames } from "@/platform/services";
import { useCurrentWorkspace } from "./useCurrentWorkspace";

const NO_NAMES: ReadonlyMap<string, string> = new Map();

/**
 * subject → tên hiển thị cho các subject đang hiện trên trang (người tạo, người xử lý...).
 * Không cần quyền xem danh bạ; tên chưa tải xong hoặc không có thì nơi dùng hiện mã rút gọn.
 */
export const usePeopleNames = (
  subjects: readonly (string | null | undefined)[],
): ReadonlyMap<string, string> => {
  const { current } = useCurrentWorkspace();
  // Sắp xếp, bỏ trùng để cùng một nhóm người dùng chung cache dù thứ tự khác nhau
  const ids = [...new Set(subjects.filter((id): id is string => Boolean(id)))].sort();
  const query = useQuery({
    queryKey: ["people", current, ids],
    // `enabled` bảo đảm current khác null khi queryFn chạy
    queryFn: ({ signal }) => getPeopleNames(current!, ids, signal),
    enabled: current !== null && ids.length > 0,
    staleTime: 5 * 60 * 1000,
  });
  return query.data ? new Map(Object.entries(query.data)) : NO_NAMES;
};
