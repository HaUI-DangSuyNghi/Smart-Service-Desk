import { IconHistory, IconMessageCircle, IconPlus } from "@tabler/icons-react";
import { cn } from "cn";
import { useAuth } from "@/platform/human/auth";
import { useCurrentWorkspace } from "@/platform/human/workspace";
import { Button } from "@/platform/shared/ui/button";
import { ErrorState } from "@/platform/shared/ui/ErrorState";
import { Page, PageHeader, PageToolbar } from "@/platform/shared/ui/page";
import { Skeleton } from "@/platform/shared/ui/Skeleton";
import { historyKey } from "./knowledgeChatHistory";
import { ChatComposer } from "./knowledge-chat/ChatComposer";
import { ChatHistory, ChatHistoryMobile } from "./knowledge-chat/ChatHistory";
import { AIAvatar, ChatMessages } from "./knowledge-chat/ChatMessages";
import { SourcePreview } from "./knowledge-chat/SourcePreview";
import { useKnowledgeChat } from "./knowledge-chat/useKnowledgeChat";

const Chat = ({ workspace, storageKey }: { workspace: string; storageKey: string }) => {
  const chat = useKnowledgeChat(workspace, storageKey);
  const isEmpty = chat.turns.length === 0;
  const historyProps = {
    history: chat.history,
    activeId: chat.activeId,
    busy: chat.busy,
    onOpen: chat.openConversation,
    onDelete: chat.deleteConversation,
  };
  return (
    <Page>
      <PageHeader title="Hỏi đáp tài liệu" icon={IconMessageCircle} />
      <PageToolbar
        start={<ChatHistoryMobile {...historyProps} />}
        end={
          <Button
            variant="outline"
            size="sm"
            className="min-h-9 md:min-h-7"
            aria-label="Cuộc trò chuyện mới"
            disabled={chat.busy || isEmpty}
            onClick={() => chat.openConversation()}
          >
            <IconPlus size={14} />
            <span>Cuộc trò chuyện mới</span>
          </Button>
        }
      />
      {chat.storageFailed && (
        <p role="alert" className="border-b border-crm-line px-4 py-2 text-crm-xs text-crm-danger">
          Không đọc hoặc lưu được lịch sử trên trình duyệt.
        </p>
      )}
      <div className="flex min-h-0 flex-1">
        <aside className="hidden min-h-0 w-48 shrink-0 flex-col gap-3 border-r border-crm-line bg-crm-subtle p-3 md:flex lg:w-52">
          <h2 className="flex items-center gap-2 px-2 text-crm-xs font-medium text-crm-text-3">
            <IconHistory size={14} />
            Gần đây
          </h2>
          <ChatHistory {...historyProps} />
        </aside>
        <div
          className={cn(
            "flex min-h-0 min-w-0 flex-1 flex-col bg-crm-surface",
            isEmpty && "justify-center",
          )}
        >
          {chat.documents.error && (
            <div className="shrink-0 px-4">
              <ErrorState error={chat.documents.error} />
            </div>
          )}
          {chat.tooBroad && (
            <p role="status" className="shrink-0 px-4 py-2 text-crm-xs text-crm-text-2">
              Thư viện có hơn 50 tài liệu. Chọn một tài liệu trong Phạm vi để tìm chính xác hơn.
            </p>
          )}
          <div
            ref={(element) => chat.bindConversation(element)}
            onScroll={chat.onScroll}
            className={cn("min-h-0 overflow-y-auto", isEmpty ? "shrink" : "flex-1")}
          >
            {isEmpty ? (
              <div className="mx-auto flex max-w-3xl flex-col items-center px-4 pb-4 pt-8 text-center sm:px-6">
                <AIAvatar className="mb-4 size-12 bg-crm-subtle [&_svg]:size-6" />
                <h2 className="text-crm-xl font-medium text-crm-text">
                  Bạn muốn hỏi gì về tài liệu?
                </h2>
                {chat.documents.isPending && (
                  <div role="status" aria-label="Đang tải tài liệu" className="mt-4">
                    <Skeleton className="w-40" />
                  </div>
                )}
                {!chat.documents.isPending &&
                  !chat.documents.data?.total &&
                  !chat.documents.error && (
                    <p role="status" className="mt-3 text-crm-sm text-crm-text-3">
                      Thư viện chưa có tài liệu.
                    </p>
                  )}
              </div>
            ) : (
              <ChatMessages turns={chat.turns} onPreview={chat.setPreview} />
            )}
          </div>
          <ChatComposer
            question={chat.question}
            documentId={chat.documentId}
            documents={chat.documents.data?.items ?? []}
            total={chat.documents.data?.total ?? 0}
            isPending={chat.documents.isPending}
            busy={chat.busy}
            tooBroad={chat.tooBroad}
            onQuestionChange={chat.setQuestion}
            onDocumentChange={chat.onDocumentChange}
            onSubmit={(event) => void chat.submit(event)}
            onStop={chat.onStop}
          />
        </div>
      </div>
      <SourcePreview
        workspace={workspace}
        source={chat.preview}
        onClose={() => chat.setPreview(null)}
      />
    </Page>
  );
};

const KnowledgeChatPage = () => {
  const { current } = useCurrentWorkspace();
  const { user } = useAuth();
  const storageKey =
    current && user ? historyKey(user.profile.iss ?? "", user.profile.sub, current) : null;
  return current && storageKey ? (
    <Chat key={storageKey} workspace={current} storageKey={storageKey} />
  ) : (
    <Page>
      <PageHeader title="Hỏi đáp tài liệu" icon={IconMessageCircle} />
      <p className="p-6 text-crm-sm text-crm-text-2">Chọn workspace để bắt đầu.</p>
    </Page>
  );
};

export default KnowledgeChatPage;
