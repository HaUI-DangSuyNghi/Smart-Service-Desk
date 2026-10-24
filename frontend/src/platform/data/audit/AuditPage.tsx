import { useState } from "react";
import { useNavigate } from "react-router";
import { IconBolt, IconClock, IconHistory, IconInfoCircle, IconUser } from "@tabler/icons-react";
import { ACTIVITY_ICONS, describeActivity } from "@/platform/human/activity";
import {
  PERMISSIONS,
  useCurrentWorkspace,
  useMembers,
  personName,
  usePeopleNames,
  usePermissions,
} from "@/platform/human/workspace";
import { outcomeTone, useProcessLabels } from "@/platform/process";
import type { ActivityEntry } from "@/platform/services";
import { formatDateTime, formatRelativeTime } from "@/platform/shared/lib/format";
import { DataTable, type DataTableColumn } from "@/platform/shared/ui/DataTable";
import { EmptyState } from "@/platform/shared/ui/EmptyState";
import { ErrorState } from "@/platform/shared/ui/ErrorState";
import { Page, PageBody, PageHeader, PageToolbar } from "@/platform/shared/ui/page";
import { Pagination } from "@/platform/shared/ui/Pagination";
import { SelectField } from "@/platform/shared/ui/SelectField";
import { TableSkeleton } from "@/platform/shared/ui/Skeleton";
import { Tag } from "@/platform/shared/ui/Tag";
import { ViewTabs } from "@/platform/shared/ui/ViewTabs";
import { lastDays, type DateRange } from "../dashboard/dashboardRange";
import { AUDIT_ACTIONS, AUDIT_PERIODS, type AuditPeriodKey } from "./auditFilters";
import { AUDIT_PAGE_SIZE, useWorkspaceAudit } from "./useWorkspaceAudit";

const rangeOf = (key: AuditPeriodKey): Partial<DateRange> => {
  const days = AUDIT_PERIODS.find((period) => period.key === key)?.days;
  return days ? lastDays(days) : {};
};

/** Nhật ký của cả workspace cho điều phối viên: ai làm gì, lúc nào; lọc theo thời gian, thao tác, người. */
const AuditPage = () => {
  const navigate = useNavigate();
  const { current } = useCurrentWorkspace();
  const { can } = usePermissions();
  const labels = useProcessLabels();
  // Bộ lọc theo người cần danh bạ; không có quyền xem thành viên thì ẩn bộ lọc này
  const canFilterPeople = can(PERMISSIONS.memberView);
  const members = useMembers(canFilterPeople);

  const [periodKey, setPeriodKey] = useState<AuditPeriodKey>("7");
  const [range, setRange] = useState(() => rangeOf("7"));
  const [action, setAction] = useState("");
  const [actor, setActor] = useState("");
  const [page, setPage] = useState(0);
  const audit = useWorkspaceAudit({ ...range, action, actor, page });
  const items = audit.data?.items ?? [];
  const names = usePeopleNames(items.flatMap((entry) => [entry.actor, entry.details.assignee]));

  // Đổi bộ lọc thì về trang đầu
  const changeFilter = (apply: () => void) => {
    apply();
    setPage(0);
  };

  const columns: readonly DataTableColumn<ActivityEntry>[] = [
    {
      key: "time",
      header: "Thời gian",
      icon: IconClock,
      width: "150px",
      cell: (entry) => (
        <time dateTime={entry.occurredAt} title={formatDateTime(entry.occurredAt)}>
          {formatRelativeTime(entry.occurredAt)}
        </time>
      ),
    },
    {
      key: "actor",
      header: "Người làm",
      icon: IconUser,
      width: "180px",
      cell: (entry) => personName(names, entry.actor),
    },
    {
      key: "action",
      header: "Thao tác",
      icon: IconBolt,
      width: "260px",
      cell: (entry) => {
        const view = describeActivity(entry, labels, names);
        const Icon = ACTIVITY_ICONS[view.kind];
        return (
          <span className="flex items-center gap-1.5">
            <Icon size={16} stroke={2} className="shrink-0 text-crm-text-3" />
            <span className="truncate">{view.title}</span>
          </span>
        );
      },
    },
    {
      key: "detail",
      header: "Chi tiết",
      icon: IconInfoCircle,
      cell: (entry) => {
        const view = describeActivity(entry, labels, names);
        return (
          <span className="flex items-center gap-2">
            {view.detail && <span className="truncate text-crm-text-2">{view.detail}</span>}
            {view.outcome && (
              <Tag tone={outcomeTone(view.outcome)}>
                {labels.outcomeLabel(entry.processKey, view.outcome)}
              </Tag>
            )}
          </span>
        );
      },
    },
  ];

  return (
    <Page>
      <PageHeader title="Nhật ký" icon={IconHistory} />
      <PageToolbar
        start={
          <div className="flex flex-wrap items-center gap-2">
            <ViewTabs
              label="Khoảng thời gian"
              tabs={AUDIT_PERIODS.map((period) => ({ key: period.key, label: period.label }))}
              value={periodKey}
              onChange={(key) =>
                changeFilter(() => {
                  setPeriodKey(key);
                  setRange(rangeOf(key));
                })
              }
            />
            <SelectField
              aria-label="Lọc theo thao tác"
              placeholder="Mọi thao tác"
              options={AUDIT_ACTIONS}
              value={action}
              onValueChange={(next) => changeFilter(() => setAction(next))}
            />
            {canFilterPeople && (
              <SelectField
                aria-label="Lọc theo người làm"
                placeholder="Mọi người"
                options={(members.data ?? []).map((member) => ({
                  value: member.subject,
                  label: member.fullName ?? member.username,
                }))}
                value={actor}
                onValueChange={(next) => changeFilter(() => setActor(next))}
              />
            )}
          </div>
        }
        end={
          audit.data && (
            <Pagination
              page={page}
              total={audit.data.total}
              pageSize={AUDIT_PAGE_SIZE}
              onPageChange={setPage}
            />
          )
        }
      />
      <PageBody>
        {current === null && <EmptyState icon={IconHistory} title="Chưa thuộc workspace nào" />}
        {current !== null && audit.isPending && (
          <TableSkeleton columns={4} label="Đang tải nhật ký" />
        )}
        {audit.error && <ErrorState error={audit.error} />}
        {audit.data && items.length === 0 && (
          <EmptyState
            icon={IconHistory}
            title="Không có hoạt động nào"
            description="Thử khoảng thời gian dài hơn hoặc bỏ bớt bộ lọc."
          />
        )}
        {items.length > 0 && (
          <DataTable
            columns={columns}
            rows={items}
            getRowKey={(entry) => entry.id}
            onRowClick={(entry) => {
              const href = describeActivity(entry, labels, names).href;
              if (href) navigate(href);
            }}
          />
        )}
      </PageBody>
    </Page>
  );
};

export default AuditPage;
