import { Link, useParams } from "react-router";
import { IconArrowLeft, IconDownload, IconFileText } from "@tabler/icons-react";
import { ApiError } from "@/platform/shared/api";
import { formatDateTime } from "@/platform/shared/lib/format";
import { Button } from "@/platform/shared/ui/button";
import { ErrorState } from "@/platform/shared/ui/ErrorState";
import { FieldRow } from "@/platform/shared/ui/FieldRow";
import { Page, PageBody, PageHeader } from "@/platform/shared/ui/page";
import { FieldsSkeleton, TextSkeleton } from "@/platform/shared/ui/Skeleton";
import { toastError } from "@/platform/shared/ui/toast";
import { useDownloadKnowledgeFile, useKnowledgeContent, useKnowledgeRecord } from "./useKnowledge";

/** Chi tiết một tài liệu: provenance (nguồn, phiên bản, quyền, checksum) và nội dung văn bản. */
const KnowledgeDetailPage = () => {
  const { id = "" } = useParams();
  const record = useKnowledgeRecord(id);
  const fileId = record.data?.fileId ?? null;
  const content = useKnowledgeContent(id, fileId !== null);
  const download = useDownloadKnowledgeFile();

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
              <Link to="/knowledge" aria-label="Về danh sách tài liệu">
                <IconArrowLeft size={14} stroke={2} />
                {/* Điện thoại chỉ giữ mũi tên để tiêu đề còn chỗ */}
                <span className="hidden sm:inline">Tài liệu</span>
              </Link>
            </Button>
            {fileId && (
              <Button
                size="sm"
                disabled={download.isPending}
                onClick={() =>
                  download.mutate(fileId, {
                    onError: (error) => toastError("Không tải được file.", error),
                  })
                }
              >
                <IconDownload size={14} stroke={2} />
                {download.isPending ? "Đang tải..." : "Tải về"}
              </Button>
            )}
          </>
        }
      />
      <PageBody>
        {record.isPending && <FieldsSkeleton rows={6} label="Đang tải tài liệu" />}
        {record.error && <ErrorState error={record.error} />}

        {record.data && (
          <>
            <dl className="border-b border-crm-line px-4 py-3">
              <FieldRow label="Nguồn">{record.data.source}</FieldRow>
              <FieldRow label="Phiên bản">{record.data.version}</FieldRow>
              <FieldRow label="Quyền sử dụng">{record.data.usageRights}</FieldRow>
              <FieldRow label="Người tạo">{record.data.ownerName ?? "—"}</FieldRow>
              <FieldRow label="Ngày tạo">{formatDateTime(record.data.createdAt)}</FieldRow>
              <FieldRow label="Checksum SHA-256">
                <span className="font-mono text-crm-sm">{record.data.checksum}</span>
              </FieldRow>
            </dl>

            <section className="px-4 py-4">
              {!fileId && <p className="text-crm-text-3">Bản ghi này không kèm file nội dung.</p>}
              {fileId !== null && content.isPending && (
                <TextSkeleton lines={8} label="Đang tải nội dung" />
              )}
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
