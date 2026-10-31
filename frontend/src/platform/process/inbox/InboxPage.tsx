import { Link, useSearchParams } from "react-router";
import { IconCalendarTime, IconInbox, IconList } from "@tabler/icons-react";
import {
  PERMISSIONS,
  useCurrentWorkspace,
  useMyPermissions,
  usePermissions,
} from "@/platform/human/workspace";
import { Button } from "@/platform/shared/ui/button";
import { EmptyState } from "@/platform/shared/ui/EmptyState";
import { ErrorState } from "@/platform/shared/ui/ErrorState";
import { Page, PageBody, PageHeader } from "@/platform/shared/ui/page";
import { PageSkeleton } from "@/platform/shared/ui/Skeleton";
import { InboxListView } from "./InboxListView";
import { WorkCalendar } from "./calendar/WorkCalendar";

const InboxPage = () => {
  const { current } = useCurrentWorkspace();
  const { can, isPending } = usePermissions();
  const permissions = useMyPermissions();
  const [params, setParams] = useSearchParams();
  const view = params.get("view") === "list" ? "list" : "calendar";
  if (isPending) return <PageSkeleton label="Đang kiểm tra quyền công việc" />;
  return (
    <Page>
      <PageHeader
        title="Công việc"
        icon={IconInbox}
        actions={
          !permissions.error &&
          current &&
          can(PERMISSIONS.taskHandle) && (
            <>
              <div role="group" aria-label="Cách xem công việc" className="flex items-center gap-1">
                {(["calendar", "list"] as const).map((value) => {
                  const Icon = value === "calendar" ? IconCalendarTime : IconList;
                  const label = value === "calendar" ? "Lịch" : "Danh sách";
                  return (
                    <Button
                      key={value}
                      variant={view === value ? "secondary" : "ghost"}
                      size="sm"
                      className="min-h-9 md:min-h-7"
                      aria-pressed={view === value}
                      aria-label={label}
                      onClick={() => setParams(value === "list" ? { view: "list" } : {})}
                    >
                      <Icon size={16} />
                      <span className="hidden sm:inline">{label}</span>
                    </Button>
                  );
                })}
              </div>
              <Button variant="outline" size="sm" className="min-h-9 md:min-h-7" asChild>
                <Link to="/duty" aria-label="Ca trực">
                  <IconCalendarTime size={16} />
                  <span className="hidden sm:inline">Ca trực</span>
                </Link>
              </Button>
            </>
          )
        }
      />
      {permissions.error ? (
        <PageBody>
          <ErrorState error={permissions.error} />
        </PageBody>
      ) : !current ? (
        <PageBody>
          <EmptyState icon={IconInbox} title="Chưa thuộc workspace nào" />
        </PageBody>
      ) : !can(PERMISSIONS.taskHandle) ? (
        <PageBody>
          <EmptyState icon={IconInbox} title="Bạn không có quyền xem công việc." />
        </PageBody>
      ) : view === "calendar" ? (
        <WorkCalendar key={current} />
      ) : (
        <InboxListView key={current} />
      )}
    </Page>
  );
};

export default InboxPage;
