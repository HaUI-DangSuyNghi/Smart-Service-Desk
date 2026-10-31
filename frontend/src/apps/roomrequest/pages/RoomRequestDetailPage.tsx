import { Link, useParams } from "react-router";
import { IconDoor } from "@tabler/icons-react";
import { personName, useCurrentWorkspace, usePeopleNames } from "../../../platform/human/workspace";
import { SuggestionCard } from "../../../platform/intelligence";
import { ProcessTimeline, useTimeline } from "../../../platform/process";
import { RequestChangeHistory } from "../../../platform/process/requests/RequestActions";
import { useRequestManagement } from "../../../platform/process/requests/useRequestManagement";
import { formatDateTime } from "../../../platform/shared/lib/format";
import { Collapsible } from "../../../platform/shared/ui/Collapsible";
import { ErrorState } from "../../../platform/shared/ui/ErrorState";
import { FieldRow } from "../../../platform/shared/ui/FieldRow";
import { Page, PageBody, PageHeader } from "../../../platform/shared/ui/page";
import { FieldsSkeleton } from "../../../platform/shared/ui/Skeleton";
import { suggestRoom } from "../services/roomRequestService";
import { useRoomRequest } from "../requests/useRoomRequests";
import { RoomOwnerActions } from "../requests/RoomOwnerActions";
import { RoomRequestStateTag } from "../requests/RoomRequestStateTag";

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
  const processId = request.data?.process.processInstanceId ?? "";
  const timeline = useTimeline(processId);
  const management = useRequestManagement(processId, Boolean(processId));
  const names = usePeopleNames([
    request.data?.creator,
    ...(timeline.data?.currentTasks.map((task) => task.assignee) ?? []),
    ...(timeline.data?.steps.map((step) => step.assignee) ?? []),
  ]);
  const reviewer =
    timeline.data?.currentTasks[0]?.assignee ??
    [...(timeline.data?.steps ?? [])].reverse().find((step) => step.key === "approve")?.assignee;

  return (
    <Page>
      <PageHeader title="Chi tiết đặt phòng" icon={IconDoor} backTo="/roomrequest" />
      <PageBody>
        {request.isPending && <FieldsSkeleton rows={5} label="Đang tải yêu cầu" />}
        {request.error && <ErrorState error={request.error} />}
        {request.data && current && (
          <div className="flex flex-col lg:grid lg:h-full lg:grid-cols-[minmax(0,1fr)_20rem]">
            <div className="order-2 min-w-0 lg:order-1 lg:overflow-y-auto">
              <div className="border-b border-crm-line px-4 py-3">
                <p className="mb-1 text-crm-sm text-crm-text-3">
                  <Link to="/roomrequest" className="hover:underline">
                    Đặt phòng
                  </Link>
                  {" › "}
                  {id.slice(0, 8)}
                </p>
                <h2 className="text-crm-xl font-semibold wrap-break-word">
                  Phòng {request.data.room}
                </h2>
              </div>
              <section className="border-b border-crm-line px-4 py-3">
                <h3 className="mb-2 font-medium">Nội dung yêu cầu</h3>
                <dl>
                  <FieldRow label="Phòng">{request.data.room}</FieldRow>
                </dl>
              </section>
              <SuggestionCard
                key={`${current}/${id}`}
                fetchSuggestion={() => suggestRoom(current, id)}
                fieldLabels={SUGGESTION_LABELS}
                evidenceLabels={EVIDENCE_LABELS}
              />
              {management.data && (
                <div className="border-t border-crm-line px-4 py-3">
                  <RequestChangeHistory view={management.data} />
                </div>
              )}
            </div>
            <aside className="order-1 min-w-0 border-b border-crm-line lg:order-2 lg:overflow-x-hidden lg:overflow-y-auto lg:border-b-0 lg:border-l">
              <div className="flex items-center gap-2 border-b border-crm-line px-4 py-3">
                {timeline.isPending ? (
                  <span className="text-crm-text-3">Đang tải trạng thái…</span>
                ) : timeline.data ? (
                  <RoomRequestStateTag request={timeline.data} />
                ) : (
                  <ErrorState error={timeline.error} />
                )}
              </div>
              <RoomOwnerActions processId={processId} creator={request.data.creator} />
              <Collapsible title="Chi tiết">
                <dl>
                  <FieldRow label="Phòng">{request.data.room}</FieldRow>
                  <FieldRow label="Mã yêu cầu">
                    <span className="font-mono" title={id}>
                      {id.slice(0, 8)}
                    </span>
                  </FieldRow>
                  <FieldRow label="Người tạo">{personName(names, request.data.creator)}</FieldRow>
                  {timeline.data && (
                    <>
                      <FieldRow label="Tạo lúc">{formatDateTime(timeline.data.startedAt)}</FieldRow>
                      {timeline.data.endedAt && (
                        <FieldRow label="Kết thúc">
                          {formatDateTime(timeline.data.endedAt)}
                        </FieldRow>
                      )}
                      <FieldRow label="Người duyệt">
                        {reviewer ? personName(names, reviewer) : "Chưa nhận"}
                      </FieldRow>
                    </>
                  )}
                </dl>
              </Collapsible>
              <Collapsible title="Tiến độ" defaultOpen>
                <div className="-mx-4">
                  <RoomProgress processInstanceId={processId} />
                </div>
              </Collapsible>
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
  return <ProcessTimeline timeline={timeline.data} compact />;
};

export default RoomRequestDetailPage;
