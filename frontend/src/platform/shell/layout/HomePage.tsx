import { Link } from "react-router";
import { IconHome, IconSparkles } from "@tabler/icons-react";
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
              <Button variant="outline" size="sm" className="min-h-9 md:min-h-7" asChild>
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
          <div key={current} className="mx-auto flex w-full max-w-7xl flex-col gap-6 p-4 lg:p-6">
            <div className="flex min-w-0 flex-wrap items-start justify-between gap-3 border-b border-crm-line pb-5">
              <div className="min-w-0">
                <p className="mb-1 truncate text-crm-sm text-crm-text-3">{currentName}</p>
                <h2 className="break-words text-crm-xl font-medium text-crm-text">
                  {greetingByHour()}
                  {me.data?.username ? `, ${me.data.username}` : ""}
                </h2>
              </div>
              <time
                dateTime={new Date().toLocaleDateString("en-CA")}
                className="pt-1 text-crm-sm text-crm-text-3"
              >
                {new Date().toLocaleDateString("vi-VN", {
                  weekday: "long",
                  day: "2-digit",
                  month: "2-digit",
                  year: "numeric",
                })}
              </time>
            </div>
            <HomeShortcuts appMenu={appMenu} />
            <div
              className={cn(
                "grid min-w-0 grid-cols-1 items-start gap-4",
                canRead && (canHandle || canCreate) && "xl:grid-cols-3",
              )}
            >
              {(canHandle || canCreate) && (
                <div className={cn("flex min-w-0 flex-col gap-4", canRead && "xl:col-span-2")}>
                  {canHandle && <HomeWorkPanel />}
                  {canCreate && <HomeRequestsPanel />}
                </div>
              )}
              {canRead && <HomeKnowledgePanel />}
            </div>
          </div>
        )}
      </PageBody>
    </Page>
  );
};

export default HomePage;
