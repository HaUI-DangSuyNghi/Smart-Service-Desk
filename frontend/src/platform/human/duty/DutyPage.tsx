import { IconCalendarTime } from "@tabler/icons-react";
import { EmptyState } from "@/platform/shared/ui/EmptyState";
import { Page, PageBody, PageHeader } from "@/platform/shared/ui/page";
import { useCurrentWorkspace } from "../workspace/useCurrentWorkspace";
import { WeekSchedule } from "./WeekSchedule";

/**
 * Ca trực (R3): kỹ thuật viên chọn hình thức (Theo ca / Hành chính) và đăng ký ca; trong ca tự
 * online, ngoài ca tự nghỉ, tắt công tắc là nghỉ đột xuất. Điều phối viên xem lịch cả workspace.
 */
const DutyPage = () => {
  const { current } = useCurrentWorkspace();

  return (
    <Page>
      <PageHeader title="Ca trực" icon={IconCalendarTime} />
      <PageBody>
        {current === null ? (
          <EmptyState icon={IconCalendarTime} title="Chưa thuộc workspace nào" />
        ) : (
          <div className="flex min-h-full flex-col p-3 md:p-4">
            <WeekSchedule />
          </div>
        )}
      </PageBody>
    </Page>
  );
};

export default DutyPage;
