import { useEffect, useRef, useState, type FormEvent } from "react";
import { useQuery } from "@tanstack/react-query";
import { Link } from "react-router";
import {
  IconArrowUp,
  IconBook,
  IconMessageCircle,
  IconPlus,
  IconSquare,
  IconTrash,
  IconSparkles,
} from "@tabler/icons-react";
import { useCurrentWorkspace } from "@/platform/human/workspace";
import { getKnowledgeContent, searchKnowledge } from "@/platform/services/knowledgeService";
import { askKnowledge, type KnowledgeSource } from "@/platform/services/knowledgeChatService";
import { ApiError } from "@/platform/shared/api";
import { Button } from "@/platform/shared/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from "@/platform/shared/ui/dialog";
import { ErrorState } from "@/platform/shared/ui/ErrorState";
import { Page, PageHeader } from "@/platform/shared/ui/page";
import { Textarea } from "@/platform/shared/ui/textarea";
import { useAuth } from "@/platform/human/auth";
import {
  historyKey,
  loadHistory,
  saveHistory,
  type Conversation,
  type Turn,
} from "./knowledgeChatHistory";

const SourcePreview = ({
  workspace,
  source,
  onClose,
}: {
  workspace: string;
  source: KnowledgeSource | null;
  onClose: () => void;
}) => {
  const content = useQuery({
    queryKey: ["knowledge", workspace, "content", source?.metadataId],
    queryFn: ({ signal }) => getKnowledgeContent(workspace, source!.metadataId, signal),
    enabled: source !== null,
  });
  const text = content.data?.text ?? "";
  const offset = source ? text.indexOf(source.excerpt) : -1;
  const highlight = useRef<HTMLElement>(null);
  useEffect(() => {
    if (offset >= 0) highlight.current?.scrollIntoView({ block: "center" });
  }, [source, offset]);
  return (
    <Dialog
      open={source !== null}
      onOpenChange={(open) => {
        if (!open) onClose();
      }}
    >
      <DialogContent className="max-h-[85dvh] overflow-y-auto sm:max-w-3xl">
        <DialogHeader>
          <DialogTitle>{source?.title ?? "Xem tài liệu"}</DialogTitle>
          <DialogDescription>Phiên bản {source?.version} · Chỉ xem trước</DialogDescription>
        </DialogHeader>
        {content.isPending && <p role="status">Đang mở tài liệu…</p>}
        {content.error && <ErrorState error={content.error} />}
        {content.data && (
          <article className="rounded-xl border border-crm-line bg-crm-surface p-5 text-sm leading-7 wrap-break-word whitespace-pre-wrap">
            {offset < 0 ? (
              text
            ) : (
              <>
                {text.slice(0, offset)}
                <mark ref={highlight} className="rounded bg-amber-100 px-0.5 text-amber-950">
                  {source?.excerpt}
                </mark>
                {text.slice(offset + (source?.excerpt.length ?? 0))}
              </>
            )}
          </article>
        )}
      </DialogContent>
    </Dialog>
  );
};

const Answer = ({ turn, preview }: { turn: Turn; preview: (source: KnowledgeSource) => void }) => (
  <>
    <div className="text-sm leading-7 wrap-break-word whitespace-pre-wrap">
      {turn.answer.split(/(\[\d+\])/).map((part, index) => {
        const match = /^\[(\d+)\]$/.exec(part);
        const source = match && turn.sources.find((item) => item.index === Number(match[1]));
        return source ? (
          <button
            key={index}
            type="button"
            onClick={() => preview(source)}
            aria-label={`Đọc nguồn ${source.index}: ${source.title}`}
            className="mx-0.5 rounded bg-sky-50 px-1.5 font-medium text-sky-800 underline decoration-sky-200 underline-offset-2 focus-visible:outline-2"
          >
            {part}
          </button>
        ) : (
          part
        );
      })}
    </div>
    {turn.state === "done" && (
      <p className="mt-3 text-xs text-crm-text-3">
        {turn.grounded
          ? "Có nguồn đối chiếu. AI có thể diễn giải sai; hãy kiểm tra đoạn trích."
          : "Chưa có đủ căn cứ trong tài liệu đã chọn."}
      </p>
    )}
    {(turn.state === "error" || turn.state === "stopped") && (
      <p role="alert" className="mt-3 text-xs text-crm-danger">
        {turn.error ?? "Đã dừng trả lời."}{" "}
        {turn.answer && "Phần trả lời này chưa được kiểm tra hoàn tất."}
      </p>
    )}
  </>
);

const EMPTY_TURNS: Turn[] = [];

const Chat = ({ workspace, storageKey }: { workspace: string; storageKey: string }) => {
  const [initial] = useState(() => loadHistory(storageKey));
  const [history, setHistory] = useState(initial.conversations);
  const [activeId, setActiveId] = useState<string | null>(initial.conversations[0]?.id ?? null);
  const active = history.find((item) => item.id === activeId);
  const turns = active?.turns ?? EMPTY_TURNS;
  const [storageFailed, setStorageFailed] = useState(initial.failed);
  const snapshot = useRef<Conversation[] | null>(null);
  const [question, setQuestion] = useState("");
  const [documentId, setDocumentId] = useState(initial.conversations[0]?.documentId ?? "");
  const [busy, setBusy] = useState(false);
  const [preview, setPreview] = useState<KnowledgeSource | null>(null);
  const controller = useRef<AbortController | null>(null);
  const conversation = useRef<HTMLDivElement>(null);
  const follow = useRef(true);
  const documents = useQuery({
    queryKey: ["knowledge", workspace, "chat-documents"],
    queryFn: ({ signal }) => searchKnowledge(workspace, { size: 100 }, signal),
  });
  useEffect(() => {
    if (history === initial.conversations) return;
    snapshot.current = history;
    const timer = window.setTimeout(() => setStorageFailed(!saveHistory(storageKey, history)), 250);
    return () => window.clearTimeout(timer);
  }, [history, initial.conversations, storageKey]);
  useEffect(() => {
    const flush = () => {
      if (snapshot.current !== null) saveHistory(storageKey, snapshot.current);
    };
    window.addEventListener("pagehide", flush);
    return () => {
      window.removeEventListener("pagehide", flush);
      flush();
    };
  }, [storageKey]);
  const openConversation = (item?: Conversation) => {
    if (controller.current) return;
    setActiveId(item?.id ?? null);
    setDocumentId(item?.documentId ?? "");
    setQuestion("");
    setPreview(null);
    follow.current = true;
  };
  const deleteConversation = (item: Conversation) => {
    if (controller.current || !window.confirm("Xóa cuộc trò chuyện này trên trình duyệt?")) return;
    const remaining = history.filter((entry) => entry.id !== item.id);
    setHistory(remaining);
    if (activeId === item.id) openConversation(remaining[0]);
  };
  const historyList = (
    <nav
      aria-label="Lịch sử hỏi đáp"
      className="max-h-[45dvh] space-y-1 overflow-y-auto md:max-h-none md:flex-1"
    >
      {history.length === 0 && (
        <p className="px-2 py-3 text-xs text-crm-text-3">Chưa có cuộc trò chuyện.</p>
      )}
      {history.map((item) => (
        <div
          key={item.id}
          className={`flex min-w-0 items-center rounded-lg ${item.id === activeId ? "bg-crm-hover" : "hover:bg-crm-hover"}`}
        >
          <button
            type="button"
            disabled={busy}
            onClick={() => openConversation(item)}
            aria-current={item.id === activeId ? "true" : undefined}
            title={item.turns[0].question}
            className="min-w-0 flex-1 truncate rounded-lg px-2 py-2.5 text-left text-xs focus-visible:outline-2"
          >
            {item.turns[0].question.slice(0, 80)}
          </button>
          <Button
            type="button"
            variant="ghost"
            size="icon-sm"
            disabled={busy}
            aria-label={`Xóa cuộc trò chuyện: ${item.turns[0].question.slice(0, 80)}`}
            onClick={() => deleteConversation(item)}
          >
            <IconTrash size={14} />
          </Button>
        </div>
      ))}
    </nav>
  );
  const tooBroad = !documentId && (documents.data?.total ?? 0) > 50;
  useEffect(() => () => controller.current?.abort(), []);
  useEffect(() => {
    if (follow.current && conversation.current)
      conversation.current.scrollTop = conversation.current.scrollHeight;
  }, [turns]);
  const submit = async (event: FormEvent) => {
    event.preventDefault();
    const value = question.trim();
    if (!value || value.length > 2000 || controller.current || !documents.data?.total || tooBroad)
      return;
    const abort = new AbortController();
    controller.current = abort;
    setBusy(true);
    setQuestion("");
    follow.current = true;
    const id = crypto.randomUUID();
    const conversationId = activeId ?? crypto.randomUUID();
    setActiveId(conversationId);
    const nextTurn: Turn = { id, question: value, answer: "", sources: [], state: "searching" };
    setHistory((previous) =>
      [
        {
          id: conversationId,
          documentId,
          updatedAt: Date.now(),
          turns: [
            ...(previous.find((item) => item.id === conversationId)?.turns ?? []),
            nextTurn,
          ].slice(-100),
        },
        ...previous.filter((item) => item.id !== conversationId),
      ].slice(0, 20),
    );
    const update = (change: (turn: Turn) => Turn) =>
      setHistory((previous) =>
        previous.map((item) =>
          item.id === conversationId
            ? {
                ...item,
                turns: item.turns.map((turn) => (turn.id === id ? change(turn) : turn)),
              }
            : item,
        ),
      );
    try {
      await askKnowledge(
        workspace,
        value,
        documentId ? [documentId] : [],
        (message) => {
          if (abort.signal.aborted) return;
          if (message.type === "sources")
            update((turn) => ({ ...turn, sources: message.sources, state: "streaming" }));
          if (message.type === "delta")
            update((turn) => ({ ...turn, answer: turn.answer + message.text, state: "streaming" }));
          if (message.type === "done")
            update((turn) => ({ ...turn, state: "done", grounded: message.grounded }));
        },
        abort.signal,
      );
    } catch (error) {
      update((turn) => ({
        ...turn,
        state: abort.signal.aborted ? "stopped" : "error",
        error: abort.signal.aborted
          ? undefined
          : error instanceof ApiError
            ? (error.detail ?? "Không kết nối được AI. Hãy thử lại.")
            : error instanceof Error
              ? error.message
              : "Không kết nối được AI. Hãy thử lại.",
      }));
    } finally {
      controller.current = null;
      setBusy(false);
    }
  };
  return (
    <Page>
      <PageHeader
        title="Hỏi đáp tài liệu"
        icon={IconMessageCircle}
        actions={
          <Button
            variant="outline"
            size="sm"
            disabled={busy || !turns.length}
            onClick={() => openConversation()}
          >
            <IconPlus size={14} />
            Cuộc trò chuyện mới
          </Button>
        }
      />
      {storageFailed && (
        <p role="alert" className="px-4 py-2 text-xs text-crm-danger">
          Không đọc hoặc lưu được lịch sử trên trình duyệt. Chat vẫn hoạt động; hãy kiểm tra dung lượng và quyền lưu trữ.
        </p>
      )}
      <div className="flex min-h-0 flex-1 flex-col md:flex-row">
        <aside className="shrink-0 border-b border-crm-line bg-crm-surface p-3 md:flex md:w-56 md:flex-col md:overflow-y-auto md:border-r md:border-b-0">
          <details className="md:hidden">
            <summary className="cursor-pointer py-1 text-xs text-crm-text-2">
              Gần đây ({history.length})
            </summary>
            {historyList}
          </details>
          <div className="hidden min-h-0 flex-1 flex-col md:flex">
            <h2 className="mb-2 px-2 text-xs text-crm-text-3">Gần đây</h2>
            {historyList}
          </div>
        </aside>
        <div className="flex min-w-0 flex-1 flex-col">
          <div className="flex flex-wrap items-center gap-2 border-b border-crm-line px-4 py-2.5 text-xs">
            <IconBook size={15} className="text-crm-text-2" />
            <label htmlFor="knowledge-scope" className="text-crm-text-2">
              Phạm vi
            </label>
            <select
              id="knowledge-scope"
              value={documentId}
              disabled={busy || documents.isPending}
              onChange={(event) => {
                const value = event.target.value;
                setDocumentId(value);
                if (activeId)
                  setHistory((previous) =>
                    previous.map((item) =>
                      item.id === activeId ? { ...item, documentId: value } : item,
                    ),
                  );
              }}
              className="min-w-0 max-w-full flex-1 rounded-md border border-crm-line bg-crm-surface px-2 py-1.5 text-crm-text outline-offset-2 sm:max-w-96"
            >
              <option value="">Tất cả tài liệu ({documents.data?.total ?? 0})</option>
              {documents.data?.items.map((document) => (
                <option key={document.id} value={document.id}>
                  {document.title}
                </option>
              ))}
            </select>
            <Link to="/knowledge" className="ml-auto text-crm-text-2 underline underline-offset-2">
              Mở thư viện ↗
            </Link>
          </div>
          {documents.error && (
            <div className="p-4">
              <ErrorState error={documents.error} />
            </div>
          )}
          {tooBroad && (
            <p role="status" className="px-4 py-2 text-xs text-crm-text-2">
              Thư viện có hơn 50 tài liệu. Chọn một tài liệu trong Phạm vi để tìm chính xác hơn.
            </p>
          )}
          <div
            ref={conversation}
            onScroll={() => {
              const element = conversation.current;
              if (element)
                follow.current =
                  element.scrollHeight - element.scrollTop - element.clientHeight < 80;
            }}
            className="min-h-0 flex-1 overflow-y-auto bg-[radial-gradient(ellipse_at_top,_#f0f7fa_0%,_transparent_65%)] px-4 py-6 sm:px-8"
          >
            {!turns.length ? (
              <div className="mx-auto flex min-h-full max-w-2xl flex-col justify-center py-8">
                <div className="mb-5 flex size-12 items-center justify-center rounded-2xl border border-sky-100 bg-sky-50 text-sky-800">
                  <IconSparkles size={25} stroke={1.5} />
                </div>
                <h2 className="text-xl font-medium text-crm-text">Bạn muốn hỏi gì?</h2>
                <div className="mt-7 grid gap-2 sm:grid-cols-2">
                  {[
                    "Quy trình xử lý sự cố được hướng dẫn như thế nào?",
                    "Tôi cần chuẩn bị thông tin gì trước khi gửi yêu cầu?",
                  ].map((suggestion) => (
                    <button
                      type="button"
                      key={suggestion}
                      onClick={() => setQuestion(suggestion)}
                      className="rounded-xl border border-crm-line bg-crm-surface p-4 text-left text-sm leading-6 text-crm-text-2 hover:bg-crm-hover focus-visible:outline-2"
                    >
                      {suggestion}
                      <IconArrowUp size={16} className="mt-3 rotate-45 text-crm-text-3" />
                    </button>
                  ))}
                </div>
                {!documents.isPending && !documents.data?.total && !documents.error && (
                  <p role="status" className="mt-5 text-sm text-crm-text-2">
                    Thư viện chưa có tài liệu. Nhờ chuyên viên tải tài liệu lên trước khi hỏi.
                  </p>
                )}
              </div>
            ) : (
              <div
                className="mx-auto max-w-3xl space-y-8 pb-4"
                role="log"
                aria-label="Nội dung hỏi đáp"
              >
                {turns.map((turn) => (
                  <section key={turn.id}>
                    <div className="mb-5 ml-auto max-w-[90%] rounded-2xl rounded-tr-sm border border-crm-line bg-crm-surface px-4 py-3 text-sm leading-6 wrap-break-word whitespace-pre-wrap">
                      {turn.question}
                    </div>
                    <div className="mb-2 flex items-center gap-2 text-xs font-medium text-crm-text-2">
                      <IconSparkles size={15} />
                      Trợ lý tài liệu
                    </div>
                    <Answer turn={turn} preview={setPreview} />
                    {(turn.state === "searching" || turn.state === "streaming") && (
                      <p role="status" className="mt-3 text-xs text-crm-text-3">
                        {turn.state === "searching"
                          ? "Đang tìm đoạn tài liệu liên quan…"
                          : "Đang đọc và trả lời…"}
                      </p>
                    )}
                  </section>
                ))}
              </div>
            )}
          </div>
          <div className="shrink-0 border-t border-crm-line bg-crm-surface px-4 py-3 sm:px-8">
            <form onSubmit={(event) => void submit(event)} className="mx-auto max-w-3xl">
              <label htmlFor="knowledge-question" className="sr-only">
                Câu hỏi về tài liệu
              </label>
              <div className="relative rounded-xl border border-crm-line p-1 focus-within:border-crm-focus">
                <Textarea
                  id="knowledge-question"
                  value={question}
                  onChange={(event) => setQuestion(event.target.value)}
                  maxLength={2000}
                  placeholder="Hỏi về tài liệu trong workspace…"
                  disabled={busy || !documents.data?.total}
                  className="min-h-20 border-0 pr-14 shadow-none focus-visible:ring-0"
                  onKeyDown={(event) => {
                    if (
                      event.key === "Enter" &&
                      !event.shiftKey &&
                      !event.nativeEvent.isComposing
                    ) {
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
                    onClick={() => controller.current?.abort()}
                    className="absolute right-2 bottom-2"
                  >
                    <IconSquare size={15} />
                  </Button>
                ) : (
                  <Button
                    type="submit"
                    size="icon"
                    aria-label="Gửi câu hỏi"
                    disabled={!question.trim() || !documents.data?.total}
                    className="absolute right-2 bottom-2"
                  >
                    <IconArrowUp size={18} />
                  </Button>
                )}
              </div>
            </form>
          </div>
        </div>
      </div>
      <SourcePreview workspace={workspace} source={preview} onClose={() => setPreview(null)} />
    </Page>
  );
};

export default function KnowledgeChatPage() {
  const { current } = useCurrentWorkspace();
  const { user } = useAuth();
  const storageKey =
    current && user ? historyKey(user.profile.iss ?? "", user.profile.sub, current) : null;
  return current && storageKey ? (
    <Chat key={storageKey} workspace={current} storageKey={storageKey} />
  ) : (
    <Page>
      <PageHeader title="Hỏi đáp tài liệu" icon={IconMessageCircle} />
      <p className="p-6 text-sm text-crm-text-2">Chọn workspace để bắt đầu.</p>
    </Page>
  );
}
