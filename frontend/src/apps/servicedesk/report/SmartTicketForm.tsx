import { useEffect, useRef } from "react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { IconLoader2, IconSend } from "@tabler/icons-react";
import { cn } from "cn";
import { z } from "zod";
import { ApiError } from "../../../platform/shared/api";
import { submitOnEnter } from "../../../platform/shared/lib/submitOnEnter";
import { Button } from "../../../platform/shared/ui/button";
import { Input } from "../../../platform/shared/ui/input";
import { Textarea } from "../../../platform/shared/ui/textarea";
import type { TicketView } from "../services/ticketService";
import { MAX_DESCRIPTION, MAX_TITLE } from "../tickets/ticketRules";
import { AnswerInput } from "./AnswerInput";
import { ChatBubble } from "./ChatBubble";
import { useSmartReport } from "./useSmartReport";

// Khớp NewTicket: không để trống, tiêu đề không xuống dòng
const schema = z.object({
  title: z
    .string()
    .trim()
    .min(1, "Nhập tiêu đề")
    .max(MAX_TITLE, `Tối đa ${MAX_TITLE} ký tự`)
    .refine((value) => !/[\r\n\t]/.test(value), "Tiêu đề viết trên một dòng"),
  description: z
    .string()
    .trim()
    .min(1, "Mô tả sự cố")
    .max(MAX_DESCRIPTION, `Tối đa ${MAX_DESCRIPTION} ký tự`),
});

type FormValues = z.infer<typeof schema>;

const errorText = (error: unknown) => {
  if (error instanceof ApiError && error.status === 409) return "Yêu cầu này đã được gửi trước đó.";
  const requestId = error instanceof ApiError ? error.requestId : null;
  return `Không gửi được yêu cầu, bạn thử lại nhé.${requestId ? ` (Mã yêu cầu: ${requestId})` : ""}`;
};

interface Props {
  /** Đã tạo ticket (câu hỏi thêm có thể còn). */
  onSent: (ticket: TicketView) => void;
  /** Đủ thông tin: form tự đóng. */
  onFinished: (ticket: TicketView) => void;
}

/**
 * Form gửi yêu cầu kiểu trợ lý: trên là hội thoại, dưới là vùng nhập. Gửi xong vùng nhập mờ đi
 * trong lúc chờ; AI muốn hỏi thêm thì vùng nhập chỉ còn một ô trả lời; đủ thông tin thì đóng.
 */
export const SmartTicketForm = ({ onSent, onFinished }: Props) => {
  const smart = useSmartReport({ onSent, onFinished });
  const form = useForm<FormValues>({
    resolver: zodResolver(schema),
    defaultValues: { title: "", description: "" },
  });
  const { errors } = form.formState;
  const working = smart.phase === "working";
  const endRef = useRef<HTMLDivElement>(null);

  // Câu mới thì cuộn tới cuối khung hội thoại
  useEffect(() => {
    endRef.current?.scrollIntoView({ block: "end" });
  }, [smart.lines.length]);

  const handleSubmit = form.handleSubmit((values) => void smart.send(values));

  return (
    <div className="flex min-h-0 flex-col gap-3">
      <div className="flex max-h-[40svh] min-h-24 flex-col gap-3 overflow-y-auto" aria-live="polite">
        {smart.lines.map((line, index) => (
          <ChatBubble key={index} from={line.from}>
            {line.text}
          </ChatBubble>
        ))}
        <div ref={endRef} />
      </div>

      <div
        aria-busy={working}
        className={cn(
          "flex flex-col gap-2 border-t border-crm-line pt-3 motion-safe:transition-opacity",
          working && "pointer-events-none opacity-50",
        )}
      >
        {smart.question ? (
          <AnswerInput
            key={`${smart.question.slot}-${smart.lines.length}`}
            question={smart.question}
            disabled={working}
            onAnswer={(value) => void smart.answer(value)}
          />
        ) : (
          <form onSubmit={handleSubmit} onKeyDown={submitOnEnter} className="flex flex-col gap-2">
            <Input
              aria-label="Tiêu đề"
              maxLength={MAX_TITLE}
              placeholder="Tiêu đề, ví dụ: Máy 15 phòng 1304 không lên nguồn"
              readOnly={working}
              {...form.register("title")}
            />
            {errors.title && <p className="text-crm-sm text-crm-danger">{errors.title.message}</p>}
            <div className="flex items-end gap-2">
              <Textarea
                aria-label="Mô tả"
                rows={3}
                maxLength={MAX_DESCRIPTION}
                placeholder="Mô tả: sự cố ra sao, ảnh hưởng mấy máy, có đang bị dừng việc không..."
                readOnly={working}
                className="min-w-0 flex-1 resize-none"
                {...form.register("description")}
              />
              <Button
                type="submit"
                size="icon"
                aria-label="Gửi yêu cầu"
                disabled={working}
                className="size-9 shrink-0"
              >
                <IconSend size={16} stroke={2} />
              </Button>
            </div>
            {errors.description && (
              <p className="text-crm-sm text-crm-danger">{errors.description.message}</p>
            )}
          </form>
        )}
      </div>

      {working && (
        <p role="status" className="flex items-center gap-1.5 text-crm-sm text-crm-text-2">
          <IconLoader2 size={14} stroke={2} className="motion-safe:animate-spin" />
          {smart.ticket ? "AI đang kiểm tra thông tin..." : "Đang gửi yêu cầu..."}
        </p>
      )}
      {smart.error !== null && (
        <p role="alert" className="text-crm-sm text-crm-danger">
          {errorText(smart.error)}
        </p>
      )}
    </div>
  );
};
