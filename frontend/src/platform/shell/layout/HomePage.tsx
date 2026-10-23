import { IconHome } from "@tabler/icons-react";
import { useMe } from "@/platform/human/workspace";
import { Page, PageBody, PageHeader } from "./page";

const HomePage = () => {
  const { data: me } = useMe();

  return (
    <Page>
      <PageHeader title="Trang chủ" icon={IconHome} />
      <PageBody>
        <div className="flex flex-col gap-1 p-4">
          <p className="font-medium">Xin chào{me ? `, ${me.username}` : ""}</p>
          <p className="text-crm-text-2">Chọn một mục ở thanh bên để bắt đầu.</p>
        </div>
      </PageBody>
    </Page>
  );
};

export default HomePage;
