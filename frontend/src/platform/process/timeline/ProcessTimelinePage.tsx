import { useParams } from "react-router";
import { IconTimeline } from "@tabler/icons-react";
import { ErrorState } from "@/platform/shared/ui/ErrorState";
import { Page, PageBody, PageHeader } from "@/platform/shared/ui/page";
import { FieldsSkeleton } from "@/platform/shared/ui/Skeleton";
import { useProcessLabels } from "../catalog/useProcessCatalog";
import { ProcessTimeline } from "./ProcessTimeline";
import { useTimeline } from "./useTimeline";

/** Trang tiến độ /processes/:id, dùng cho mọi quy trình. */
const ProcessTimelinePage = () => {
  const { id = "" } = useParams();
  const timeline = useTimeline(id);
  const labels = useProcessLabels();
  const title = timeline.data ? labels.processName(timeline.data.processKey) : "Tiến độ";

  return (
    <Page>
      <PageHeader title={title} icon={IconTimeline} />
      <PageBody>
        {timeline.isPending && <FieldsSkeleton rows={7} label="Đang tải tiến độ" />}
        {timeline.error && <ErrorState error={timeline.error} />}
        {timeline.data && <ProcessTimeline timeline={timeline.data} />}
      </PageBody>
    </Page>
  );
};

export default ProcessTimelinePage;
