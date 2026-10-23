import type { ReactNode } from "react";
import { Link, useParams } from "react-router";
import { IconArrowLeft, IconDownload, IconFileText } from "@tabler/icons-react";
import { useMemberNames } from "@/platform/human/workspace";
import { ApiError } from "@/platform/shared/api";
import { formatDateTime } from "@/platform/shared/lib/format";
import { Button } from "@/platform/shared/ui/button";
import { ErrorState } from "@/platform/shared/ui/ErrorState";
import { Loading } from "@/platform/shared/ui/Loading";
import { Page, PageBody, PageHeader } from "@/platform/shared/ui/page";
import { useDownloadKnowledgeFile, useKnowledgeContent, useKnowledgeRecord } from "./useKnowledge";

// Điện thoại: nhãn nằm trên giá trị; từ sm: nhãn cột trái rộng 128px
const FieldRow = ({ label, children }: { label: string; children: ReactNode }) => (
  <div className="flex min-h-8 flex-col gap-0.5 py-1.5 sm:flex-row sm:items-start sm:gap-4">
    <dt className="shrink-0 text-crm-text-3 sm:w-32">{label}</dt>
    <dd className="min-w-0 break-all text-crm-text">{children}</dd>
  </div>
);

/** Chi tiết một tài liệu: provenance (nguồn, phiên bản, quyền, checksum) và nội dung văn bản. */
const KnowledgeDetailPage = () => {
  const { id = "" } = useParams();
  const record = useKnowledgeRecord(id);
  const fileId = record.data?.fileId ?? null;
  const content = useKnowledgeContent(id, fileId !== null);
  const download = useDownloadKnowledgeFile();
  const memberNames = useMemberNames();

  // Backend trả 400 khi file không phải văn bản: không phải lỗi, chỉ là không xem trước được
  const notPreviewable = content.error instanceof ApiError && content.error.status === 400;

  return (
    <Page>
      <PageHeader
        title={record.data?.title ?? "Tài liệu"}
        icon={IconFileText}
        actions={
          <>
            <Button variant="outline" size="sm" asChild>
              <Link to="/knowledge" aria-label="Về danh sách tri thức">
                <IconArrowLeft size={14} stroke={2} />
                {/* Điện thoại chỉ giữ mũi tên để tiêu đề còn chỗ */}
                <span className="hidden sm:inline">Tri thức</span>
              </Link>
            </Button>
            {fileId && (
              <Button
                size="sm"
                disabled={download.isPending}
                onClick={() => download.mutate(fileId)}
              >
                <IconDownload size={14} stroke={2} />
                {download.isPending ? "Đang tải..." : "Tải về"}
              </Button>
            )}
          </>
        }
      />
      <PageBody>
        <Loading active={record.isPending} label="Đang tải tài liệu" />
        {record.error && <ErrorState error={record.error} />}
        {download.error && <ErrorState error={download.error} fallback="Không tải được file." />}

        {record.data && (
          <>
            <dl className="border-b border-crm-line px-4 py-3">
              <FieldRow label="Nguồn">{record.data.source}</FieldRow>
              <FieldRow label="Phiên bản">{record.data.version}</FieldRow>
              <FieldRow label="Quyền sử dụng">{record.data.usageRights}</FieldRow>
              <FieldRow label="Người tạo">{memberNames.get(record.data.owner) ?? "—"}</FieldRow>
              <FieldRow label="Ngày tạo">{formatDateTime(record.data.createdAt)}</FieldRow>
              <FieldRow label="Checksum SHA-256">
                <span className="font-mono text-crm-sm">{record.data.checksum}</span>
              </FieldRow>
            </dl>

            <section className="relative min-h-40 px-4 py-4">
              {!fileId && <p className="text-crm-text-3">Bản ghi này không kèm file nội dung.</p>}
              <Loading active={fileId !== null && content.isPending} label="Đang tải nội dung" />
              {notPreviewable && (
                <p className="text-crm-text-3">
                  Định dạng này không xem trước được, hãy bấm Tải về.
                </p>
              )}
              {content.error && !notPreviewable && <ErrorState error={content.error} />}
              {/* Hiển thị dạng chữ thuần, không render HTML/markdown để tránh XSS.
                  wrap-break-word: link, chuỗi dài không ngắt được sẽ đẩy trang cuộn ngang */}
              {content.data && (
                <pre className="font-crm text-crm-md leading-relaxed wrap-break-word whitespace-pre-wrap text-crm-text">
                  {content.data.text}
                </pre>
              )}
            </section>
          </>
        )}
      </PageBody>
    </Page>
  );
};

export default KnowledgeDetailPage;
