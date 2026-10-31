import { Link } from "react-router";
import { IconDoor } from "@tabler/icons-react";
import { Button } from "../../../platform/shared/ui/button";
import { EmptyState } from "../../../platform/shared/ui/EmptyState";
import { Page, PageBody, PageHeader, PageToolbar } from "../../../platform/shared/ui/page";
import { CreateRoomRequestDialog } from "../requests/CreateRoomRequestDialog";

/**
 * Trang của app đặt phòng. Backend không có API liệt kê riêng:
 * yêu cầu đã gửi xem ở trang "Yêu cầu" chung (khối P) cùng mọi quy trình khác.
 */
const RoomRequestHomePage = () => {
  return (
    <Page>
      <PageHeader title="Đặt phòng" icon={IconDoor} />
      <PageToolbar end={<CreateRoomRequestDialog />} />
      <PageBody>
        <EmptyState
          icon={IconDoor}
          title="Đặt phòng học, phòng họp"
          description="Gửi yêu cầu, điều phối viên duyệt trong mục Công việc. AI gợi ý phân loại để tham khảo."
          action={
            <Button variant="outline" size="sm" asChild>
              <Link to="/requests">Xem yêu cầu của tôi</Link>
            </Button>
          }
        />
      </PageBody>
    </Page>
  );
};

export default RoomRequestHomePage;
