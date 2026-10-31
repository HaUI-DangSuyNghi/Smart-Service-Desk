import { IconError404 } from "@tabler/icons-react";
import { Link } from "react-router";
import { Page, PageBody, PageHeader } from "@/platform/shared/ui/page";

const NotFoundPage = () => {
  return (
    <Page>
      <PageHeader title="Không tìm thấy trang" icon={IconError404} />
      <PageBody>
        <div className="flex flex-col items-start gap-2 p-4">
          <p className="text-crm-text-2">Đường dẫn không tồn tại hoặc đã bị đổi.</p>
          <Link to="/home" className="text-crm-accent hover:underline">
            Về trang chủ
          </Link>
        </div>
      </PageBody>
    </Page>
  );
};

export default NotFoundPage;
