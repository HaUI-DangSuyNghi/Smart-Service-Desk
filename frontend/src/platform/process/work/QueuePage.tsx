import { useState } from "react";
import { useNavigate } from "react-router";
import { IconListNumbers } from "@tabler/icons-react";
import { useDutyList } from "@/platform/human/duty";
import { PERMISSIONS, useActor, useCurrentWorkspace, usePermissions } from "@/platform/human/workspace";
import type { QueueItem } from "@/platform/services";
import { EmptyState } from "@/platform/shared/ui/EmptyState";
import { ErrorState } from "@/platform/shared/ui/ErrorState";
import { Page, PageBody, PageHeader, PageToolbar } from "@/platform/shared/ui/page";
import { SelectField } from "@/platform/shared/ui/SelectField";
import { TableSkeleton } from "@/platform/shared/ui/Skeleton";
import { processLink } from "../common/processLinks";
import { QueueTable } from "./QueueTable";
import { useQueue } from "./useWork";
import { formatMinutes } from "./workLabels";

/**
 * Hàng đợi (R4): việc được giao đã sắp thứ tự, việc Đang làm ghim đầu, có dự kiến bắt đầu.
 * Điều phối viên chọn người để xem hàng đợi của người khác.
 */
const QueuePage = () => {
  const navigate = useNavigate();
  const { current } = useCurrentWorkspace();
  const { actor } = useActor();
  const { can } = usePermissions();
  const canAssign = can(PERMISSIONS.taskAssign);
  const [person, setPerson] = useState("");
  const duty = useDutyList(canAssign);
  const queue = useQueue(person || undefined);
  const items = queue.data?.items ?? [];
  const isMine = !person || person === actor?.subject;

  const options = (duty.data ?? []).map((status) => ({
    value: status.subject,
    label: status.onDuty ? status.displayName : `${status.displayName} (nghỉ)`,
  }));

  const handleOpen = (item: QueueItem) =>
    navigate(processLink(item.processKey, item.businessKey) ?? `/processes/${item.processInstanceId}`);

  return (
    <Page>
      <PageHeader title="Hàng đợi" icon={IconListNumbers} />
      <PageToolbar
        start={
          queue.data && (
            <span className="text-crm-text-2">
              {queue.data.total} việc · tải{" "}
              <span className="font-medium text-crm-text">
                {formatMinutes(queue.data.loadMinutes)}
              </span>
            </span>
          )
        }
        end={
          canAssign && (
            <SelectField
              aria-label="Xem hàng đợi của"
              placeholder="Hàng đợi của tôi"
              options={options}
              value={person}
              onValueChange={setPerson}
              className="w-full sm:w-56"
            />
          )
        }
      />
      <PageBody>
        {current === null && <EmptyState icon={IconListNumbers} title="Chưa thuộc workspace nào" />}
        {current !== null && queue.isPending && (
          <TableSkeleton columns={6} label="Đang tải hàng đợi" />
        )}
        {queue.error && <ErrorState error={queue.error} />}
        {queue.data && items.length === 0 && (
          <EmptyState
            icon={IconListNumbers}
            title="Hàng đợi trống"
            description="Việc điều phối viên giao cho bạn sẽ hiện ở đây theo thứ tự cần làm."
          />
        )}
        {items.length > 0 && <QueueTable items={items} showActions={isMine} onOpen={handleOpen} />}
      </PageBody>
    </Page>
  );
};

export default QueuePage;
