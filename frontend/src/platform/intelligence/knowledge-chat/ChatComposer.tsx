import type { FormEvent } from "react";
import { Link } from "react-router";
import { IconArrowUp, IconBook, IconSquare, IconX } from "@tabler/icons-react";
import { Button } from "@/platform/shared/ui/button";
import { Textarea } from "@/platform/shared/ui/textarea";
import { ChatScopePicker } from "./ChatScopePicker";

interface Props {
  question: string;
  documentId: string;
  documents: readonly { id: string; title: string }[];
  total: number;
  isPending: boolean;
  busy: boolean;
  tooBroad: boolean;
  onQuestionChange: (value: string) => void;
  onDocumentChange: (value: string) => void;
  onSubmit: (event: FormEvent) => void;
  onStop: () => void;
}

export const ChatComposer = ({
  question,
  documentId,
  documents,
  total,
  isPending,
  busy,
  tooBroad,
  onQuestionChange,
  onDocumentChange,
  onSubmit,
  onStop,
}: Props) => {
  const selected = documents.find((item) => item.id === documentId);
  return (
    <div className="mx-auto w-full max-w-3xl shrink-0 px-4 pb-4 pt-3 sm:px-6 sm:pb-6">
      <form onSubmit={onSubmit}>
        <label htmlFor="knowledge-question" className="sr-only">
          Câu hỏi về tài liệu
        </label>
        <div className="flex items-end gap-1 rounded-[26px] border border-crm-line-strong bg-crm-surface px-2 py-2 shadow-crm-light transition-colors focus-within:border-crm-focus">
          <ChatScopePicker
            documents={documents}
            total={total}
            value={documentId}
            disabled={busy || isPending}
            onChange={onDocumentChange}
          />
          <Textarea
            id="knowledge-question"
            value={question}
            rows={1}
            maxLength={2000}
            placeholder="Hỏi về tài liệu…"
            disabled={busy || !total}
            className="field-sizing-content max-h-40 min-h-9 flex-1 rounded-none border-0 px-1 py-2 text-crm-md shadow-none focus-visible:ring-0 md:text-crm-md"
            onChange={(event) => onQuestionChange(event.target.value)}
            onKeyDown={(event) => {
              if (event.key === "Enter" && !event.shiftKey && !event.nativeEvent.isComposing) {
                event.preventDefault();
                event.currentTarget.form?.requestSubmit();
              }
            }}
          />
          {busy ? (
            <Button
              type="button"
              size="icon"
              aria-label="Dừng trả lời"
              onClick={onStop}
              className="size-9 shrink-0 rounded-full"
            >
              <IconSquare size={15} />
            </Button>
          ) : (
            <Button
              type="submit"
              size="icon"
              aria-label="Gửi câu hỏi"
              disabled={!question.trim() || !total || tooBroad}
              className="size-9 shrink-0 rounded-full"
            >
              <IconArrowUp size={18} />
            </Button>
          )}
        </div>
        <div className="mt-2 flex min-w-0 flex-wrap items-center justify-between gap-x-3 gap-y-1 px-2 text-crm-xs text-crm-text-3">
          <div className="flex min-w-0 max-w-full items-center gap-1.5">
            <IconBook size={13} className="shrink-0" />
            <span className="truncate">
              {documentId
                ? (selected?.title ?? "Tài liệu đã chọn")
                : `Tất cả tài liệu${isPending ? "" : ` · ${total}`}`}
            </span>
            {documentId && (
              <button
                type="button"
                disabled={busy}
                aria-label="Bỏ chọn tài liệu"
                onClick={() => onDocumentChange("")}
                className="flex size-9 shrink-0 items-center justify-center rounded-crm-sm hover:bg-crm-hover focus-visible:outline-2 focus-visible:outline-crm-focus md:size-5"
              >
                <IconX size={12} />
              </button>
            )}
          </div>
          <Link
            to="/knowledge"
            className="inline-flex min-h-9 items-center rounded-crm-sm hover:text-crm-text focus-visible:outline-2 focus-visible:outline-crm-focus md:min-h-5"
          >
            Thư viện tài liệu
          </Link>
        </div>
      </form>
    </div>
  );
};
