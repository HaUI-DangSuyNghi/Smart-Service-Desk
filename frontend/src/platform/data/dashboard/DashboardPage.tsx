import { useState } from "react";
import {
  IconBook,
  IconChartBar,
  IconCircleCheck,
  IconClockHour4,
  IconHourglass,
  IconProgress,
  IconSend,
} from "@tabler/icons-react";
import {
  hasAnyPermission,
  PERMISSIONS,
  useCurrentWorkspace,
  usePermissions,
} from "@/platform/human/workspace";
import { formatSeconds } from "@/platform/shared/lib/format";
import { EmptyState } from "@/platform/shared/ui/EmptyState";
import { ErrorState } from "@/platform/shared/ui/ErrorState";
import { Page, PageBody, PageHeader, PageToolbar } from "@/platform/shared/ui/page";
import { TableSkeleton } from "@/platform/shared/ui/Skeleton";
import { ViewTabs } from "@/platform/shared/ui/ViewTabs";
import { useKnowledgeList } from "../knowledge/useKnowledge";
import { DailyChart } from "./DailyChart";
import { lastDays, PERIODS, summarize, type PeriodKey } from "./dashboardRange";
import { dashboardWidgets } from "./dashboardWidgets";
import { ProcessStatsTable } from "./ProcessStatsTable";
import { StatTile } from "./StatTile";
import { useProcessStatistics } from "./useDashboard";

/** Số tài liệu trong Knowledge Hub; chỉ hiện khi người xem có quyền xem tài liệu. */
const DocumentTile = () => {
  const documents = useKnowledgeList("", 0);
  return (
    <StatTile
      label="Tài liệu"
      icon={IconBook}
      value={documents.data ? String(documents.data.total) : "—"}
      hint="trong Knowledge Hub"
    />
  );
};

/** Dashboard của điều phối viên: số liệu quy trình theo khoảng thời gian và khối số liệu do app đóng góp. */
const DashboardPage = () => {
  const { current } = useCurrentWorkspace();
  const { granted, can } = usePermissions();
  const [periodKey, setPeriodKey] = useState<PeriodKey>("7");
  const period = PERIODS.find((item) => item.key === periodKey) ?? PERIODS[0];
  // Tính lại khoảng theo ngày hiện tại mỗi lần đổi lựa chọn
  const [range, setRange] = useState(() => lastDays(period.days));
  const statistics = useProcessStatistics(range);
  const totals = statistics.data ? summarize(statistics.data.processes) : null;
  const widgets = dashboardWidgets().filter((widget) =>
    hasAnyPermission(granted, widget.requiredPermissions),
  );
  const periodHint = `tạo trong ${period.label}`;

  const handlePeriodChange = (key: PeriodKey) => {
    setPeriodKey(key);
    setRange(lastDays(PERIODS.find((item) => item.key === key)?.days ?? 7));
  };

  return (
    <Page>
      <PageHeader title="Dashboard" icon={IconChartBar} />
      <PageToolbar
        start={
          <ViewTabs
            label="Khoảng thời gian"
            tabs={PERIODS.map((item) => ({ key: item.key, label: item.label }))}
            value={periodKey}
            onChange={handlePeriodChange}
          />
        }
      />
      <PageBody>
        {current === null && <EmptyState icon={IconChartBar} title="Chưa thuộc workspace nào" />}
        {current !== null && statistics.isPending && (
          <TableSkeleton columns={5} rows={6} label="Đang tải số liệu" />
        )}
        {statistics.error && <ErrorState error={statistics.error} />}
        {statistics.data && totals && (
          <div className="flex flex-col gap-6 p-4">
            <section className="grid grid-cols-2 gap-3 md:grid-cols-3 xl:grid-cols-6">
              <StatTile
                label="Yêu cầu mới"
                icon={IconSend}
                value={String(totals.started)}
                hint={periodHint}
              />
              <StatTile
                label="Đang chạy"
                icon={IconProgress}
                value={String(totals.running)}
                hint={periodHint}
              />
              <StatTile
                label="Đã kết thúc"
                icon={IconCircleCheck}
                value={String(totals.completed)}
                hint={periodHint}
              />
              <StatTile
                label="Việc chờ nhận"
                icon={IconHourglass}
                value={String(totals.waitingClaim)}
                hint={`hiện tại · ${totals.inProgress} đang xử lý`}
              />
              <StatTile
                label="Xử lý trung bình"
                icon={IconClockHour4}
                value={
                  totals.averageCompletionSeconds === null
                    ? "—"
                    : formatSeconds(totals.averageCompletionSeconds)
                }
                hint="từ lúc tạo tới khi kết thúc"
              />
              {can(PERMISSIONS.documentView) && <DocumentTile />}
            </section>

            <DailyChart daily={statistics.data.daily} />

            {statistics.data.processes.length > 0 ? (
              <ProcessStatsTable processes={statistics.data.processes} />
            ) : (
              <EmptyState
                icon={IconChartBar}
                title="Chưa có số liệu trong khoảng này"
                description="Yêu cầu tạo trong khoảng thời gian đã chọn sẽ được thống kê ở đây."
              />
            )}

            {/* Khối số liệu do app đăng ký (ví dụ thống kê ticket); dashboard chỉ cấp khung và khoảng thời gian */}
            {widgets.map((widget) => (
              <section key={widget.id} className="flex flex-col gap-2">
                <h2 className="font-medium">{widget.title}</h2>
                <widget.component from={range.from} to={range.to} />
              </section>
            ))}
          </div>
        )}
      </PageBody>
    </Page>
  );
};

export default DashboardPage;
