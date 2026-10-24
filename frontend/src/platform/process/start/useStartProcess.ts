import { useMutation, useQueryClient } from "@tanstack/react-query";
import { useCurrentWorkspace } from "@/platform/human/workspace";
import { startProcess } from "@/platform/services";
import { processKeys } from "../common/queryKeys";

export interface StartProcessInput {
  processKey: string;
  variables: Record<string, string>;
}

/** Tạo yêu cầu theo quy trình tự phục vụ; xong thì làm mới trang "Yêu cầu" và công việc. */
export const useStartProcess = () => {
  const { current } = useCurrentWorkspace();
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ processKey, variables }: StartProcessInput) => {
      if (!current) throw new Error("Chưa chọn workspace");
      return startProcess(current, processKey, variables);
    },
    onSuccess: () => queryClient.invalidateQueries({ queryKey: processKeys.all(current) }),
  });
};
