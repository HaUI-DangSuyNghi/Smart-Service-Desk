import { useState } from "react";
import { IconHistory, IconMessageCircle, IconTrash } from "@tabler/icons-react";
import { cn } from "cn";
import { Button } from "@/platform/shared/ui/button";
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from "@/platform/shared/ui/dialog";
import type { Conversation } from "../knowledgeChatHistory";

interface Props {
  history: readonly Conversation[];
  activeId: string | null;
  busy: boolean;
  onOpen: (item: Conversation) => void;
  onDelete: (item: Conversation) => void;
}

export const ChatHistory = ({ history, activeId, busy, onOpen, onDelete }: Props) => (
  <nav aria-label="Lịch sử hỏi đáp" className="min-h-0 flex-1 space-y-1 overflow-y-auto">
    {history.length === 0 && (
      <p className="px-3 py-4 text-crm-sm text-crm-text-3">Chưa có cuộc trò chuyện.</p>
    )}
    {history.map((item) => (
      <div
        key={item.id}
        className={cn(
          "flex min-w-0 items-center rounded-crm-sm",
          item.id === activeId ? "bg-crm-hover" : "hover:bg-crm-hover",
        )}
      >
        <button
          type="button"
          disabled={busy}
          onClick={() => onOpen(item)}
          aria-current={item.id === activeId ? "true" : undefined}
          title={item.turns[0].question}
          className="flex min-h-9 min-w-0 flex-1 items-center gap-2 rounded-crm-sm px-2 text-left text-crm-sm text-crm-text outline-none focus-visible:ring-2 focus-visible:ring-crm-focus"
        >
          <IconMessageCircle size={14} className="shrink-0 text-crm-text-3" />
          <span className="truncate">{item.turns[0].question.slice(0, 80)}</span>
        </button>
        <Button
          type="button"
          variant="ghost"
          size="icon-sm"
          className="size-9 shrink-0 text-crm-text-3 md:size-7"
          disabled={busy}
          aria-label={`Xóa cuộc trò chuyện: ${item.turns[0].question.slice(0, 80)}`}
          onClick={() => onDelete(item)}
        >
          <IconTrash size={14} />
        </Button>
      </div>
    ))}
  </nav>
);

export const ChatHistoryMobile = (props: Props) => {
  const [open, setOpen] = useState(false);
  return (
    <Dialog open={open} onOpenChange={setOpen}>
      <DialogTrigger asChild>
        <Button
          variant="ghost"
          size="icon-sm"
          className="size-9 md:hidden"
          aria-label="Lịch sử hỏi đáp"
        >
          <IconHistory size={16} />
        </Button>
      </DialogTrigger>
      <DialogContent
        className="flex h-[min(32rem,calc(100dvh-2rem))] flex-col gap-4"
        aria-describedby={undefined}
      >
        <DialogHeader>
          <DialogTitle>Gần đây</DialogTitle>
        </DialogHeader>
        <ChatHistory
          {...props}
          onOpen={(item) => {
            props.onOpen(item);
            setOpen(false);
          }}
        />
      </DialogContent>
    </Dialog>
  );
};
