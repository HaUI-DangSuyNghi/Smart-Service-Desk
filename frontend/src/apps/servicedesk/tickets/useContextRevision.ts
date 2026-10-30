import { useQuery } from "@tanstack/react-query";
import { useAuth } from "../../../platform/human/auth";
import { getContextRevision } from "../services/enrichmentService";

/**
 * Bản ngữ cảnh hiện tại của ticket (tăng mỗi lần người gửi bổ sung thông tin).
 * Dùng chung cache với khối Bổ sung thông tin để trang biết ticket đã được bổ sung chưa.
 */
export const useContextRevision = (workspace: string, ticketId: string) => {
  const { user } = useAuth();
  return useQuery({
    queryKey: ["servicedesk", "context-revision", workspace, ticketId, user?.profile.sub ?? ""],
    queryFn: ({ signal }) => getContextRevision(workspace, ticketId, signal),
    retry: false,
  });
};
