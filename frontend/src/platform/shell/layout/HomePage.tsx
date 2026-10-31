import { Link } from "react-router";
import { IconCalendarTime, IconHome, IconSparkles } from "@tabler/icons-react";
import { cn } from "cn";
import {
  PERMISSIONS,
  useCurrentWorkspace,
  useMe,
  useMyPermissions,
  usePermissions,
  useWorkspaces,
} from "@/platform/human/workspace";
import { Button } from "@/platform/shared/ui/button";
import { EmptyState } from "@/platform/shared/ui/EmptyState";
import { ErrorState } from "@/platform/shared/ui/ErrorState";
import { Page, PageBody, PageHeader } from "@/platform/shared/ui/page";
import { PageSkeleton } from "@/platform/shared/ui/Skeleton";
import type { MenuItem } from "../router/registry";
import { HomeKnowledgePanel } from "./home/HomeKnowledgePanel";
import { HomeRequestsPanel } from "./home/HomeRequestsPanel";
import { HomeShortcuts } from "./home/HomeShortcuts";
import { HomeWorkPanel } from "./home/HomeWorkPanel";

interface Props {
  appMenu: readonly MenuItem[];
}

const greetingByHour = () => {
  const hour = new Date().getHours();
  if (hour < 12) return "Chào buổi sáng";
  if (hour < 18) return "Chào buổi chiều";
  return "Chào buổi tối";
};

const HomePage = ({ appMenu }: Props) => {
  const me = useMe();
  const workspaces = useWorkspaces();
  const permissions = useMyPermissions();
  const { current, currentName } = useCurrentWorkspace();
  const { can, isPending } = usePermissions();
  const error = me.error ?? workspaces.error ?? permissions.error;
  const isReady = Boolean(current) && !isPending && !error;
  const canHandle = isReady && can(PERMISSIONS.taskHandle);
  const canCreate = isReady && can(PERMISSIONS.requestCreate);
  const canRead = isReady && can(PERMISSIONS.documentView);

  if (me.isPending || workspaces.isPending || isPending) {
    return <PageSkeleton label="Đang tải trang chủ" />;
  }

  return (
    <Page>
      <PageHeader
        title="Trang chủ"
        icon={IconHome}
        actions={
          <>
            {canRead && (
              <Button variant="info" size="sm" className="min-h-9 md:min-h-7" asChild>
                <Link to="/knowledge-chat" aria-label="Hỏi đáp tài liệu">
                  <IconSparkles size={14} />
                  <span className="hidden sm:inline">Hỏi đáp tài liệu</span>
                </Link>
              </Button>
            )}
          </>
        }
      />
      <PageBody>
        {error ? (
          <div className="text-center">
            <ErrorState error={error} />
            <Button
              variant="outline"
              size="sm"
              className="min-h-9 md:min-h-7"
              onClick={() => {
                void me.refetch();
                void workspaces.refetch();
                if (current) void permissions.refetch();
              }}
            >
              Thử lại
            </Button>
          </div>
        ) : !current ? (
          <EmptyState icon={IconHome} title="Chưa có workspace." />
        ) : (
          // Gỡ dữ liệu cũ khi đổi workspace, kể cả các hook đang giữ trang trước bằng placeholderData.
          <div
            key={current}
            className="flex min-h-full w-full min-w-0 flex-col gap-5 bg-crm-subtle/50 p-4 lg:p-6"
          >
            <div className="flex min-w-0 flex-wrap items-center gap-5 rounded-2xl border border-crm-tone-blue/15 bg-linear-to-r from-crm-tone-blue-soft via-crm-surface to-crm-tone-purple-soft/70 px-5 py-4 lg:gap-7 lg:px-7">
              <div className="min-w-0 flex-1 basis-56">
                <p className="mb-2 inline-flex max-w-full items-center gap-2 rounded-full border border-crm-tone-blue/15 bg-crm-surface/80 px-2.5 py-1 text-crm-xs font-medium text-crm-tone-blue">
                  <span className="size-1.5 shrink-0 rounded-full bg-crm-tone-blue" />
                  <span className="truncate">{currentName}</span>
                </p>
                <h2 className="break-words text-xl font-semibold tracking-tight text-crm-text sm:text-2xl">
                  {greetingByHour()}
                  {me.data?.username ? `, ${me.data.username}` : ""}
                </h2>
                <p className="mt-2 text-crm-sm text-crm-text-2">
                  Mọi công việc và yêu cầu của bạn, ngay tại đây.
                </p>
                <time
                  dateTime={new Date().toLocaleDateString("en-CA")}
                  className="mt-3 inline-flex items-center gap-2 rounded-xl border border-crm-line bg-crm-surface/80 px-3 py-2 text-crm-sm text-crm-text-2"
                >
                  <IconCalendarTime size={18} className="shrink-0 text-crm-tone-purple" />
                  {new Date().toLocaleDateString("vi-VN", {
                    weekday: "long",
                    day: "2-digit",
                    month: "2-digit",
                    year: "numeric",
                  })}
                </time>
              </div>
              <img
                src="/home.png"
                alt=""
                width={192}
                height={160}
                className="ml-auto h-28 w-32 shrink-0 object-contain sm:h-36 sm:w-40 lg:h-40 lg:w-48"
              />
            </div>
            <HomeShortcuts appMenu={appMenu} />
            <div
              className={cn(
                "grid min-w-0 grid-cols-1 items-start gap-4",
                ((canHandle && canCreate) || (canRead && (canHandle || canCreate))) &&
                  "lg:grid-cols-2",
              )}
            >
              {canRead && <HomeKnowledgePanel />}
              {canHandle && <HomeWorkPanel />}
              {canCreate && (
                <div className={cn("min-w-0", canRead && canHandle && "lg:col-span-2")}>
                  <HomeRequestsPanel />
                </div>
              )}
            </div>
          </div>
        )}
      </PageBody>
    </Page>
  );
};

export default HomePage;
