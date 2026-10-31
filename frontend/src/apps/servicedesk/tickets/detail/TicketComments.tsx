import { useState, type FormEvent, type KeyboardEvent } from "react";
import { useAuth } from "../../../../platform/human/auth";
import { personName, usePeopleNames } from "../../../../platform/human/workspace";
import { formatDateTime, formatRelativeTime } from "../../../../platform/shared/lib/format";
import { submitOnEnter } from "../../../../platform/shared/lib/submitOnEnter";
import { Avatar } from "../../../../platform/shared/ui/Avatar";
import { Button } from "../../../../platform/shared/ui/button";
import { ErrorState } from "../../../../platform/shared/ui/ErrorState";
import { TextSkeleton } from "../../../../platform/shared/ui/Skeleton";
import { Textarea } from "../../../../platform/shared/ui/textarea";
import { toastError } from "../../../../platform/shared/ui/toast";
import { useAddTicketComment, useTicketComments } from "../useTickets";

const MAX_COMMENT = 2000;
const AVATAR = "size-8 text-crm-sm";

interface Props {
  workspace: string;
  ticketId: string;
}

/** Kiểu Jira: ô viết bình luận ở trên (bấm mới mở rộng, Lưu / Huỷ), bên dưới bình luận mới nhất trước. */
export const TicketComments = ({ workspace, ticketId }: Props) => {
  const { user } = useAuth();
  const me = user?.profile.sub;
  const comments = useTicketComments(workspace, ticketId);
  const add = useAddTicketComment(workspace, ticketId);
  const names = usePeopleNames([me, ...(comments.data?.map((item) => item.author) ?? [])]);
  const [editing, setEditing] = useState(false);
  const [body, setBody] = useState("");
  const text = body.trim();
  // Backend trả cũ nhất trước
  const newestFirst = comments.data ? [...comments.data].reverse() : [];

  const close = () => {
    setBody("");
    setEditing(false);
  };

  const handleSubmit = (event: FormEvent) => {
    event.preventDefault();
    if (!text || add.isPending) return;
    add.mutate(text, {
      onSuccess: close,
      onError: (error) => toastError("Không gửi được bình luận.", error),
    });
  };

  const handleKeyDown = (event: KeyboardEvent<HTMLFormElement>) => {
    if (event.key === "Escape" && !add.isPending) {
      event.stopPropagation();
      close();
      return;
    }
    submitOnEnter(event);
  };

  return (
    <div className="flex flex-col gap-5 px-4 py-4">
      <div className="flex gap-3">
        <Avatar name={me ? personName(names, me) : "?"} className={AVATAR} />
        {editing ? (
          <form
            onSubmit={handleSubmit}
            onKeyDown={handleKeyDown}
            className="flex min-w-0 flex-1 flex-col gap-2"
          >
            <Textarea
              autoFocus
              aria-label="Bình luận"
              placeholder="Viết bình luận..."
              rows={4}
              maxLength={MAX_COMMENT}
              value={body}
              onChange={(event) => setBody(event.target.value)}
              className="min-h-24 md:text-crm-md"
            />
            <div className="flex items-center gap-1">
              <Button type="submit" size="sm" disabled={!text || add.isPending}>
                Lưu
              </Button>
              <Button
                type="button"
                size="sm"
                variant="ghost"
                disabled={add.isPending}
                onClick={close}
              >
                Huỷ
              </Button>
            </div>
          </form>
        ) : (
          <button
            type="button"
            onClick={() => setEditing(true)}
            className="h-8 min-w-0 flex-1 rounded-lg border border-input px-2.5 text-left text-crm-text-3 outline-none hover:border-crm-text-3 focus-visible:border-ring focus-visible:ring-3 focus-visible:ring-ring/50"
          >
            Thêm bình luận...
          </button>
        )}
      </div>

      {comments.isPending && <TextSkeleton lines={3} label="Đang tải bình luận" />}
      {comments.error && (
        <ErrorState error={comments.error} fallback="Không tải được bình luận." />
      )}
      {comments.data?.length === 0 && (
        <p className="text-crm-sm text-crm-text-3">Chưa có bình luận</p>
      )}
      {newestFirst.length > 0 && (
        <ul className="flex flex-col gap-5">
          {newestFirst.map((item) => {
            const name = personName(names, item.author);
            return (
              <li key={item.id} className="flex gap-3">
                <Avatar name={name} className={AVATAR} />
                <div className="min-w-0 flex-1">
                  <p className="leading-tight font-semibold text-crm-text">{name}</p>
                  <time
                    dateTime={item.createdAt}
                    title={formatDateTime(item.createdAt)}
                    className="text-crm-xs text-crm-text-3"
                  >
                    {formatRelativeTime(item.createdAt)}
                  </time>
                  {/* Giữ xuống dòng người viết nhập; chuỗi dài không ngắt được thì bẻ để không tràn */}
                  <p className="mt-1.5 whitespace-pre-wrap text-crm-text wrap-anywhere">
                    {item.body}
                  </p>
                </div>
              </li>
            );
          })}
        </ul>
      )}
    </div>
  );
};
