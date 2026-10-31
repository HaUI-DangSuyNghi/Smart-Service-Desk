import type { ProcessSummary, ProcessTimeline } from "../../../platform/services";
import { Tag } from "../../../platform/shared/ui/Tag";

export const RoomRequestStateTag = ({ request }: { request: ProcessSummary | ProcessTimeline }) => {
  if (request.cancelled) return <Tag>Đã hủy</Tag>;
  if (!request.endedAt) return <Tag tone="orange">Chờ duyệt</Tag>;
  const outcome =
    "steps" in request
      ? [...request.steps].reverse().find((step) => step.key === "approve" && step.endedAt)?.outcome
      : undefined;
  if (outcome === "rejected") return <Tag tone="red">Từ chối</Tag>;
  return <Tag tone="green">{outcome === "approved" ? "Đã duyệt" : "Đã kết thúc"}</Tag>;
};
