import { useEffect, useRef } from "react";
import { Link } from "react-router";
import { useQuery } from "@tanstack/react-query";
import { IconArrowUpRight, IconFileText, IconQuote } from "@tabler/icons-react";
import { getKnowledgeContent } from "@/platform/services";
import type { KnowledgeSource } from "@/platform/services/knowledgeChatService";
import { Button } from "@/platform/shared/ui/button";
import { Dialog, DialogContent, DialogHeader, DialogTitle } from "@/platform/shared/ui/dialog";
import { ErrorState } from "@/platform/shared/ui/ErrorState";
import { Tag } from "@/platform/shared/ui/Tag";
import { TextSkeleton } from "@/platform/shared/ui/Skeleton";

const FILE_LABELS: Record<string, string> = {
  "application/pdf": "PDF",
  "application/vnd.openxmlformats-officedocument.wordprocessingml.document": "Word",
  "text/markdown": "Markdown",
  "text/plain": "Văn bản",
};

interface Props {
  workspace: string;
  source: KnowledgeSource | null;
  onClose: () => void;
}

export const SourcePreview = ({ workspace, source, onClose }: Props) => {
  const content = useQuery({
    queryKey: ["knowledge", workspace, "content", source?.metadataId],
    queryFn: ({ signal }) => getKnowledgeContent(workspace, source!.metadataId, signal),
    enabled: source !== null,
  });
  const text = content.data?.text ?? "";
  const offset = source ? text.indexOf(source.excerpt) : -1;
  const highlight = useRef<HTMLElement>(null);
  useEffect(() => {
    if (offset < 0) return;
    const frame = requestAnimationFrame(() =>
      highlight.current?.scrollIntoView({ block: "center" }),
    );
    return () => cancelAnimationFrame(frame);
  }, [source, offset]);
  return (
    <Dialog
      open={source !== null}
      onOpenChange={(open) => {
        if (!open) onClose();
      }}
    >
      <DialogContent
        className="flex h-[min(44rem,calc(100dvh-2rem))] flex-col gap-0 overflow-hidden p-0 sm:max-w-4xl"
        aria-describedby={undefined}
      >
        <DialogHeader className="shrink-0 border-b border-crm-line px-4 py-4 sm:px-6">
          <div className="flex min-w-0 items-start gap-3">
            <span className="flex size-9 shrink-0 items-center justify-center rounded-crm-md border border-crm-line text-crm-text-2">
              <IconFileText size={20} />
            </span>
            <div className="min-w-0 flex-1">
              <DialogTitle className="break-words text-crm-lg">
                {source?.title ?? "Xem tài liệu"}
              </DialogTitle>
              <div className="mt-2 flex flex-wrap items-center gap-2 text-crm-xs text-crm-text-3">
                <span>Phiên bản {source?.version}</span>
                {content.data && (
                  <Tag tone="gray">{FILE_LABELS[content.data.mediaType] ?? "Tài liệu"}</Tag>
                )}
                <span>Nội dung trích xuất</span>
              </div>
            </div>
          </div>
        </DialogHeader>
        <div className="flex min-w-0 shrink-0 flex-wrap items-center justify-between gap-2 border-b border-crm-line bg-crm-subtle px-4 py-2 sm:px-6">
          <Button
            variant="ghost"
            size="sm"
            className="min-h-9 text-crm-text-2 md:min-h-7"
            disabled={offset < 0}
            onClick={() =>
              highlight.current?.scrollIntoView({ block: "center", behavior: "instant" })
            }
          >
            <IconQuote size={14} />
            Đoạn được trích [{source?.index}]
          </Button>
          {source && (
            <Link
              to={`/knowledge/${source.metadataId}`}
              className="inline-flex min-h-9 items-center gap-1 rounded-crm-sm text-crm-xs text-crm-text-2 hover:text-crm-accent focus-visible:outline-2 focus-visible:outline-crm-focus md:min-h-7"
              onClick={onClose}
            >
              Mở chi tiết
              <IconArrowUpRight size={14} />
            </Link>
          )}
        </div>
        <div className="min-h-0 flex-1 overflow-y-auto bg-crm-subtle p-3 sm:p-6">
          {content.isPending && (
            <div className="mx-auto max-w-2xl rounded-crm-md border border-crm-line bg-crm-surface p-5 sm:p-8">
              <TextSkeleton lines={12} label="Đang mở tài liệu" />
            </div>
          )}
          {content.error && <ErrorState error={content.error} />}
          {!content.error && content.data && (
            <article className="mx-auto min-h-full max-w-2xl rounded-crm-sm border border-crm-line bg-crm-surface px-5 py-6 text-crm-md leading-7 wrap-break-word whitespace-pre-wrap text-crm-text sm:px-10 sm:py-8">
              {offset < 0 ? (
                text
              ) : (
                <>
                  {text.slice(0, offset)}
                  <mark
                    ref={highlight}
                    className="rounded-crm-xs bg-crm-tone-orange-soft px-0.5 text-crm-text"
                  >
                    {source?.excerpt}
                  </mark>
                  {text.slice(offset + (source?.excerpt.length ?? 0))}
                </>
              )}
            </article>
          )}
        </div>
      </DialogContent>
    </Dialog>
  );
};
