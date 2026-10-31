import { useQuery } from "@tanstack/react-query";
import { useAuth } from "../../../platform/human/auth";
import { getIntake } from "../services/intakeService";

// Trạng thái đã xong thì thôi hỏi lại
const SETTLED = ["REVIEWED", "CANONICAL_FOLLOWER"];
const POLL_MS = 5000;

/**
 * Trạng thái tiếp nhận (intake) của ticket; null khi ticket không qua tiếp nhận (backend 404).
 * `poll`: hỏi lại định kỳ cho tới khi xong (trang chi tiết).
 */
export const useIntake = (workspace: string, ticketId: string, { poll = false } = {}) => {
  const { user } = useAuth();
  return useQuery({
    queryKey: ["servicedesk", "intake", workspace, ticketId, user?.profile.sub],
    queryFn: ({ signal }) => getIntake(workspace, ticketId, signal),
    retry: false,
    refetchInterval: (query) =>
      poll && query.state.data && !SETTLED.includes(query.state.data.status) ? POLL_MS : false,
  });
};
