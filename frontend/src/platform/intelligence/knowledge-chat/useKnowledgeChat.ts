import { useCallback, useEffect, useRef, useState, type FormEvent } from "react";
import { useQuery } from "@tanstack/react-query";
import { searchKnowledge } from "@/platform/services";
import { askKnowledge, type KnowledgeSource } from "@/platform/services/knowledgeChatService";
import { ApiError } from "@/platform/shared/api";
import { loadHistory, saveHistory, type Conversation, type Turn } from "../knowledgeChatHistory";

const EMPTY_TURNS: Turn[] = [];

export const useKnowledgeChat = (workspace: string, storageKey: string) => {
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
  const bindConversation = useCallback((element: HTMLDivElement | null) => {
    conversation.current = element;
  }, []);
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
  const onDocumentChange = (value: string) => {
    setDocumentId(value);
    if (activeId)
      setHistory((previous) =>
        previous.map((item) => (item.id === activeId ? { ...item, documentId: value } : item)),
      );
  };
  return {
    history,
    activeId,
    turns,
    storageFailed,
    question,
    setQuestion,
    documentId,
    busy,
    preview,
    setPreview,
    documents,
    tooBroad,
    bindConversation,
    openConversation,
    deleteConversation,
    onDocumentChange,
    submit,
    onStop: () => controller.current?.abort(),
    onScroll: () => {
      const element = conversation.current;
      if (element)
        follow.current = element.scrollHeight - element.scrollTop - element.clientHeight < 80;
    },
  };
};
