import { IconLock } from "@tabler/icons-react";
import { Link } from "react-router";
import { EmptyState } from "@/platform/shared/ui/EmptyState";
import { Page, PageBody, PageHeader } from "@/platform/shared/ui/page";

/** 403: có đăng nhập nhưng vai trò ở workspace đang chọn không đủ để mở trang. */
const ForbiddenPage = () => {
  return (
    <Page>
      <PageHeader title="Không có quyền truy cập" icon={IconLock} />
      <PageBody>
        <EmptyState
          icon={IconLock}
          title="Bạn không có quyền xem trang này"
          description="Vai trò của bạn ở workspace đang chọn không đủ. Thử đổi workspace ở góc trên bên trái, hoặc liên hệ điều phối viên."
          action={
            <Link to="/home" className="text-crm-accent hover:underline">
              Về trang chủ
            </Link>
          }
        />
      </PageBody>
    </Page>
  );
};

export default ForbiddenPage;
