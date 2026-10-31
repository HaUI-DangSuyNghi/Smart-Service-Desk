import { useMemo } from "react";
import { useQuery } from "@tanstack/react-query";
import { useCurrentWorkspace } from "@/platform/human/workspace";
import { listProcesses } from "@/platform/services";
import { processKeys } from "../common/queryKeys";
import { createProcessLabels, type ProcessLabels } from "./processLabels";

/** Danh mục quy trình (nhãn, kết quả theo bước, canStart). Gần như không đổi nên cache lâu. */
export const useProcessCatalog = () => {
  const { current } = useCurrentWorkspace();
  return useQuery({
    queryKey: processKeys.catalog(current),
    // `enabled` bảo đảm current khác null khi queryFn chạy
    queryFn: ({ signal }) => listProcesses(current!, signal),
    enabled: current !== null,
    staleTime: 10 * 60 * 1000,
  });
};

/** Nhãn tra theo danh mục; danh mục chưa tải xong thì dùng giá trị dự phòng (key, tên BPMN). */
export const useProcessLabels = (): ProcessLabels => {
  const { data } = useProcessCatalog();
  return useMemo(() => createProcessLabels(data ?? []), [data]);
};
