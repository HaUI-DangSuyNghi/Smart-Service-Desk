import { useQuery } from "@tanstack/react-query";
import { useState } from "react";
import { Link } from "react-router";
import { useAuth } from "../../../platform/human/auth";
import { useActor, useCurrentWorkspace } from "../../../platform/human/workspace";
import { Button } from "../../../platform/shared/ui/button";
import { ErrorState } from "../../../platform/shared/ui/ErrorState";
import { Page, PageBody, PageHeader } from "../../../platform/shared/ui/page";
import { getPendingIntake } from "../services/intakeService";

const IntakePendingPage = () => {
  const { current } = useCurrentWorkspace();
  const { roles } = useActor();
  const { user } = useAuth();
  const allowed = roles.includes("coordinator");
  const [position, setPosition] = useState({ scope: "", page: 0 });
  const scope = `${current}/${user?.profile.sub}`;
  const page = position.scope === scope ? position.page : 0;
  const pending = useQuery({
    queryKey: ["servicedesk", "intake-pending", current, user?.profile.sub, page],
    queryFn: ({ signal }) => getPendingIntake(current!, page, signal),
    enabled: !!current && allowed,
    retry: false,
  });
  return (
    <Page>
      <PageHeader title="Ticket đang tiếp nhận" />
      <PageBody>
        {!allowed ? (
          <p role="alert" className="p-4">
            Danh sách này dành cho điều phối viên của workspace.
          </p>
        ) : (
          <div className="space-y-4 p-4">
            <p className="text-crm-text-2">
              Ticket chờ bổ sung, kiểm tra hoặc chuyển sang duyệt thủ công. Mở ticket để xem thông
              tin và quyết định tiếp theo.
            </p>
            <Button
              variant="outline"
              onClick={() => void pending.refetch()}
              disabled={pending.isFetching}
            >
              Làm mới danh sách
            </Button>
            {pending.isFetching && <p role="status">Đang tải danh sách…</p>}
            {pending.error && <ErrorState error={pending.error} />}
            {!pending.isFetching && !pending.error && pending.data?.length === 0 && (
              <p>Không có yêu cầu bổ sung đang chờ trong danh sách hiện tại.</p>
            )}
            {!pending.isFetching && !pending.error && (
              <ul className="divide-y divide-crm-line">
                {pending.data?.map((item) => (
                  <li key={item} className="flex flex-wrap items-center justify-between gap-3 py-4">
                    <div className="space-y-1">
                      <p className="font-medium">Ticket chờ tiếp nhận</p>
                    </div>
                    <Button variant="outline" asChild>
                      <Link
                        to={`/servicedesk/workspaces/${encodeURIComponent(current!)}/tickets/${encodeURIComponent(item)}`}
                      >
                        Xem ticket
                      </Link>
                    </Button>
                  </li>
                ))}
              </ul>
            )}
            <nav aria-label="Phân trang ticket cần xem xét" className="flex items-center gap-3">
              <Button
                variant="outline"
                disabled={page === 0 || pending.isFetching}
                onClick={() => setPosition({ scope, page: page - 1 })}
              >
                Trang trước
              </Button>
              <span>Trang {page + 1}</span>
              <Button
                variant="outline"
                disabled={
                  pending.isFetching ||
                  !!pending.error ||
                  pending.data?.length !== 50 ||
                  page === 10000
                }
                onClick={() => setPosition({ scope, page: page + 1 })}
              >
                Trang sau
              </Button>
            </nav>
          </div>
        )}
      </PageBody>
    </Page>
  );
};

export default IntakePendingPage;
