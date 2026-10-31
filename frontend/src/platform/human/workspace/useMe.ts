import { useQuery } from "@tanstack/react-query";
import { getMe } from "@/platform/services";

/** Người dùng hiện tại theo backend: tên, workspace và vai trò. */
export const useMe = () => {
  return useQuery({
    queryKey: ["me"],
    queryFn: ({ signal }) => getMe(signal),
    staleTime: 5 * 60 * 1000,
  });
};
