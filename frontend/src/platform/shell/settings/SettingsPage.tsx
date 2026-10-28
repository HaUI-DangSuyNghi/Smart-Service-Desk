import { IconSettings } from "@tabler/icons-react";
import { useMe } from "@/platform/human/workspace";
import { WorkSettingsSection } from "@/platform/process";
import { Page, PageBody, PageHeader } from "@/platform/shared/ui/page";
import { PageSkeleton } from "@/platform/shared/ui/Skeleton";
import ForbiddenPage from "../layout/ForbiddenPage";
import type { SettingsSection } from "../router/registry";

interface Props {
  /** Cấu hình riêng do các app đăng ký. */
  sections: readonly SettingsSection[];
}

/**
 * Cấu hình hệ thống (mục 6 của plan): một bộ cho mọi workspace, chỉ quản trị nền tảng mở được.
 * Phần chung của Tầng 2 ở trên, phần riêng của từng app ở dưới.
 */
const SettingsPage = ({ sections }: Props) => {
  const me = useMe();

  if (me.isPending) return <PageSkeleton label="Đang kiểm tra quyền" />;
  if (!me.data?.platformAdmin) return <ForbiddenPage />;

  return (
    <Page>
      <PageHeader title="Cấu hình hệ thống" icon={IconSettings} />
      <PageBody>
        <div className="mx-auto flex max-w-3xl flex-col gap-8 px-4 py-6">
          <section className="flex flex-col gap-3">
            <h2 className="text-crm-lg font-semibold">Cấu hình chung</h2>
            <WorkSettingsSection />
          </section>
          {sections.map((section) => (
            <section key={section.id} className="flex flex-col gap-3 border-t border-crm-line pt-6">
              <h2 className="text-crm-lg font-semibold">{section.title}</h2>
              <section.component />
            </section>
          ))}
        </div>
      </PageBody>
    </Page>
  );
};

export default SettingsPage;
