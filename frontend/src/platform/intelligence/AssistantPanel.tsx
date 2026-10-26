import { useId, useState, type FormEvent } from "react";
import { IconSearch } from "@tabler/icons-react";
import { Button } from "@/platform/shared/ui/button";
import { Textarea } from "@/platform/shared/ui/textarea";
import { aiErrorMessage, aiErrorRequestId } from "./aiErrors";
import { useAssistance, type AskAssistance } from "./useAssistance";

const MAX_QUESTION_LENGTH = 2000;

interface Props {
  ask: AskAssistance;
  /** Tên bản ghi đang xem, dùng trong câu mô tả ("Trợ lý không thay đổi <subjectLabel>"). */
  subjectLabel: string;
}

/** Trợ lý tri thức: hỏi đáp có trích dẫn nguồn người dùng được xem; luôn cần người kiểm tra. */
export const AssistantPanel = ({ ask, subjectLabel }: Props) => {
  const questionId = useId();
  const hintId = useId();
  const [question, setQuestion] = useState("");
  const assistance = useAssistance(ask);
  const result = assistance.data;
  const requestId = aiErrorRequestId(assistance.error);

  const handleSubmit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (assistance.isPending || !question.trim()) return;
    assistance.mutate(question);
  };

  return (
    <section className="px-4 py-4" aria-labelledby={`${questionId}-heading`}>
      <div className="mb-3 space-y-1">
        <h2 id={`${questionId}-heading`} className="font-medium">
          Trợ lý tri thức
        </h2>
        <p className="text-crm-sm text-crm-text-3">
          Tham khảo trích dẫn từ nguồn bạn được phép xem. Hãy kiểm tra hướng dẫn trước khi áp dụng.
          Trợ lý không thay đổi {subjectLabel}.
        </p>
      </div>

      <form onSubmit={handleSubmit} className="space-y-2">
        <label htmlFor={questionId} className="block text-crm-sm font-medium">
          Bạn cần hỗ trợ điều gì?
        </label>
        <Textarea
          id={questionId}
          aria-describedby={hintId}
          value={question}
          onChange={(event) => setQuestion(event.target.value)}
          maxLength={MAX_QUESTION_LENGTH}
          rows={4}
          required
          disabled={assistance.isPending}
        />
        <p id={hintId} className="text-crm-sm text-crm-text-3">
          Tối đa 2.000 ký tự. Không gửi mật khẩu hoặc thông tin bí mật.
        </p>
        <Button
          type="submit"
          className="h-9 md:h-8"
          disabled={assistance.isPending || !question.trim()}
        >
          <IconSearch size={14} stroke={2} />
          {assistance.isPending ? "Đang tìm hướng dẫn…" : "Tìm hướng dẫn"}
        </Button>
      </form>

      <div role="status" aria-atomic="true" className="mt-3 text-crm-sm">
        {assistance.isPending && "Đang kiểm tra nguồn tri thức và chuẩn bị trích dẫn."}
        {assistance.error && (
          <>
            <p>{aiErrorMessage(assistance.error)}</p>
            {requestId && <p className="text-crm-text-3">Mã yêu cầu: {requestId}</p>}
          </>
        )}
        {result?.status === "INSUFFICIENT_EVIDENCE" && (
          <p>
            Chưa có đủ bằng chứng phù hợp. Bạn có thể bổ sung câu hỏi hoặc tiếp tục xử lý thủ công.
          </p>
        )}
      </div>

      {result?.status === "SUCCESS" && (
        <div className="mt-4 space-y-3 border-t border-crm-line pt-4">
          <p className="text-crm-sm font-medium">
            Hướng dẫn tham khảo · Cần kiểm tra trước khi áp dụng
          </p>
          {/* Hiển thị dạng chữ thuần, không render HTML/markdown để tránh XSS */}
          <blockquote className="border-l-2 border-crm-line pl-3 leading-relaxed wrap-break-word whitespace-pre-wrap">
            {result.answer}
          </blockquote>
          <h3 className="text-crm-sm font-medium">Nguồn trích dẫn</h3>
          <ul className="space-y-1 text-crm-sm">
            {result.citations.map((citation) => (
              <li key={citation.citationId} className="wrap-break-word">
                <span className="font-medium">
                  [{citation.citationId}] {citation.displayTitle}
                </span>
                <span className="text-crm-text-3"> · Phiên bản {citation.sourceVersion}</span>
              </li>
            ))}
          </ul>
        </div>
      )}
    </section>
  );
};
