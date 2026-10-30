import { useParams } from "react-router";
import { IconDoor } from "@tabler/icons-react";
import { useCurrentWorkspace } from "../../../platform/human/workspace";
import { SuggestionCard } from "../../../platform/intelligence";
import { ProcessTimeline, useTimeline } from "../../../platform/process";
import { ErrorState } from "../../../platform/shared/ui/ErrorState";
import { FieldRow } from "../../../platform/shared/ui/FieldRow";
import { Page, PageBody, PageHeader } from "../../../platform/shared/ui/page";
import { FieldsSkeleton } from "../../../platform/shared/ui/Skeleton";
import { suggestRoom } from "../services/roomRequestService";
import { useRoomRequest } from "../requests/useRoomRequests";

// Nhãn theo profile room-request của AI; bằng chứng trích từ đầu vào backend dựng từ tên phòng
const SUGGESTION_LABELS = {
  category: "Loại yêu cầu",
  priority: "Mức ưu tiên",
  suggestedGroup: "Nhóm duyệt",
};
const EVIDENCE_LABELS = { title: "Tiêu đề", description: "Mô tả" };

/** Chi tiết yêu cầu đặt phòng: tiến độ (khối P) và gợi ý của AI (khối I), cùng component với ticket. */
const RoomRequestDetailPage = () => {
  const { id = "" } = useParams();
  const { current } = useCurrentWorkspace();
  const request = useRoomRequest(id);

  return (
    <Page>
      <PageHeader
        title={request.data ? `Phòng ${request.data.room}` : "Đặt phòng"}
        icon={IconDoor}
        backTo="/requests"
      />
      <PageBody>
        {request.isPending && <FieldsSkeleton rows={5} label="Đang tải yêu cầu" />}
        {request.error && <ErrorState error={request.error} />}
        {request.data && current && (
          // Màn rộng: trái là yêu cầu + tiến độ, phải là AI; điện thoại xếp dọc
          <div className="lg:grid lg:grid-cols-[minmax(0,1fr)_minmax(0,26rem)]">
            <div className="min-w-0">
              <dl className="border-b border-crm-line px-4 py-3">
                <FieldRow label="Phòng">{request.data.room}</FieldRow>
              </dl>
              <RoomProgress processInstanceId={request.data.process.processInstanceId} />
            </div>
            <aside className="min-w-0 border-t border-crm-line lg:border-t-0 lg:border-l">
              {/* key: đổi yêu cầu thì bỏ gợi ý cũ */}
              <SuggestionCard
                key={`${current}/${id}`}
                fetchSuggestion={() => suggestRoom(current, id)}
                fieldLabels={SUGGESTION_LABELS}
                evidenceLabels={EVIDENCE_LABELS}
              />
            </aside>
          </div>
        )}
      </PageBody>
    </Page>
  );
};

const RoomProgress = ({ processInstanceId }: { processInstanceId: string }) => {
  const timeline = useTimeline(processInstanceId);

  if (timeline.isPending) return <FieldsSkeleton rows={5} label="Đang tải tiến độ" />;
  if (timeline.error) return <ErrorState error={timeline.error} />;
  return <ProcessTimeline timeline={timeline.data} />;
};

export default RoomRequestDetailPage;
