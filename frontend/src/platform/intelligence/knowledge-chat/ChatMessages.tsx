import { IconCheck, IconFileText, IconSparkles, IconUser } from "@tabler/icons-react";
import { cn } from "cn";
import type { KnowledgeSource } from "@/platform/services/knowledgeChatService";
import type { Turn } from "../knowledgeChatHistory";

export const AIAvatar = ({ className }: { className?: string }) => (
  <span
    role="img"
    aria-label="AI"
    className={cn(
      "flex size-8 shrink-0 items-center justify-center rounded-full border border-crm-line bg-crm-surface text-crm-accent",
      className,
    )}
  >
    <IconSparkles size={18} stroke={1.6} aria-hidden />
  </span>
);

const ChatAnswer = ({
  turn,
  onPreview,
}: {
  turn: Turn;
  onPreview: (source: KnowledgeSource) => void;
}) => {
  const cited = new Set(
    Array.from(turn.answer.matchAll(/\[(\d+)\]/g), (match) => Number(match[1])),
  );
  return (
    <>
      <div className="text-crm-md leading-7 wrap-break-word whitespace-pre-wrap">
        {turn.answer.split(/(\[\d+\])/).map((part, index) => {
          const match = /^\[(\d+)\]$/.exec(part);
          const source = match && turn.sources.find((item) => item.index === Number(match[1]));
          return source ? (
            <button
              key={index}
              type="button"
              onClick={() => onPreview(source)}
              aria-label={`Đọc nguồn ${source.index}: ${source.title}`}
              className="mx-0.5 inline-flex min-h-9 items-center rounded-crm-sm bg-crm-accent-soft px-1.5 text-crm-sm font-medium text-crm-accent outline-none hover:underline focus-visible:ring-2 focus-visible:ring-crm-focus md:min-h-5"
            >
              {part}
            </button>
          ) : (
            part
          );
        })}
      </div>
      {turn.answer && turn.sources.some((source) => cited.has(source.index)) && (
        <div className="mt-3 flex flex-wrap gap-2">
          {turn.sources
            .filter((source) => cited.has(source.index))
            .map((source) => (
              <button
                key={source.index}
                type="button"
                onClick={() => onPreview(source)}
                aria-label={`Xem tài liệu: ${source.title}`}
                className="flex min-h-9 min-w-0 max-w-full items-center gap-1.5 rounded-crm-md border border-crm-line px-2.5 py-1.5 text-crm-xs text-crm-text-2 outline-none hover:bg-crm-hover focus-visible:ring-2 focus-visible:ring-crm-focus"
              >
                <IconFileText size={14} className="shrink-0 text-crm-text-3" />
                <span className="truncate">{source.title}</span>
                <span className="shrink-0 text-crm-text-3">[{source.index}]</span>
              </button>
            ))}
        </div>
      )}
      {turn.state === "done" && (
        <p className="mt-3 flex items-center gap-1.5 text-crm-xs text-crm-text-3">
          {turn.grounded && <IconCheck size={13} />}
          {turn.grounded ? "Có nguồn đối chiếu." : "Chưa có đủ căn cứ trong tài liệu đã chọn."}
        </p>
      )}
      {(turn.state === "error" || turn.state === "stopped") && (
        <p role="alert" className="mt-3 text-crm-xs text-crm-danger">
          {turn.error ?? "Đã dừng trả lời."}{" "}
          {turn.answer && "Phần trả lời này chưa được kiểm tra hoàn tất."}
        </p>
      )}
    </>
  );
};

export const ChatMessages = ({
  turns,
  onPreview,
}: {
  turns: readonly Turn[];
  onPreview: (source: KnowledgeSource) => void;
}) => (
  <div
    role="log"
    aria-label="Nội dung hỏi đáp"
    className="mx-auto w-full max-w-3xl space-y-8 px-4 py-6 sm:px-6"
  >
    {turns.map((turn) => (
      <section key={turn.id} className="min-w-0 space-y-5">
        <div className="flex min-w-0 items-start justify-end gap-2.5">
            <div className="max-w-[85%] rounded-crm-xl rounded-tr-crm-sm bg-crm-hover px-4 py-2.5 text-crm-md leading-6 wrap-break-word whitespace-pre-wrap">
            <span className="sr-only">Bạn: </span>
            {turn.question}
          </div>
          <span
            className="flex size-8 shrink-0 items-center justify-center rounded-full bg-crm-canvas text-crm-text-2"
            aria-label="Bạn"
          >
            <IconUser size={16} />
          </span>
        </div>
        <div className="flex min-w-0 items-start gap-2.5">
          <AIAvatar />
          <div className="min-w-0 flex-1 pt-1">
            <p className="mb-1.5 text-crm-sm font-medium text-crm-text">Trợ lý tài liệu</p>
            <ChatAnswer turn={turn} onPreview={onPreview} />
            {(turn.state === "searching" || turn.state === "streaming") && (
              <div
                role="status"
                aria-label={
                  turn.state === "searching"
                    ? "Đang tìm đoạn tài liệu liên quan"
                    : "Đang đọc và trả lời"
                }
                className="mt-2 flex h-6 items-center gap-1.5 text-crm-text-3"
              >
                {[0, 1, 2].map((dot) => (
                  <span
                    key={dot}
                    className="size-1.5 rounded-full bg-crm-text-3 motion-safe:animate-pulse"
                  />
                ))}
              </div>
            )}
          </div>
        </div>
      </section>
    ))}
  </div>
);
