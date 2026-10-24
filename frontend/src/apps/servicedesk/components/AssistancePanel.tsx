import { useEffect, useId, useRef, useState } from "react";
import type { FormEvent } from "react";
import { ApiError } from "../../../platform/shared/api";
import { askAssistance } from "../api/assistance";
import type { Assistance } from "../api/assistance";

interface Props {
  workspace: string;
  ticketId: string;
}

function errorMessage(error: unknown) {
  if (error instanceof ApiError) {
    if (error.status === 401) return "Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.";
    if (error.status === 403 || error.status === 404)
      return "Trợ lý chưa khả dụng hoặc bạn không còn quyền truy cập.";
    if (error.status === 409) return "Nguồn tri thức đã thay đổi. Vui lòng gửi lại câu hỏi.";
    if (error.status === 502)
      return "Không thể xác minh câu trả lời. Vui lòng xử lý ticket theo quy trình hiện hành.";
  }

  return "Trợ lý tạm thời không phản hồi. Bạn vẫn có thể xử lý ticket theo quy trình hiện hành.";
}

export default function AssistancePanel({ workspace, ticketId }: Props) {
  const questionId = useId();
  const hintId = useId();
  const active = useRef<AbortController | null>(null);
  const [question, setQuestion] = useState("");
  const [pending, setPending] = useState(false);
  const [result, setResult] = useState<Assistance | null>(null);
  const [error, setError] = useState("");

  useEffect(() => {
    return () => {
      active.current?.abort();
      active.current = null;
    };
  }, []);

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (pending || !question.trim()) return;

    active.current?.abort();
    const controller = new AbortController();
    active.current = controller;
    setPending(true);
    setResult(null);
    setError("");

    try {
      const response = await askAssistance(workspace, ticketId, question, controller.signal);
      if (!controller.signal.aborted && active.current === controller) setResult(response);
    } catch (failure) {
      if (!controller.signal.aborted && active.current === controller)
        setError(errorMessage(failure));
    } finally {
      if (active.current === controller) {
        setPending(false);
        active.current = null;
      }
    }
  }

  return (
    <section
      className="rounded-xl border bg-card p-5 text-card-foreground sm:p-6"
      aria-labelledby={`${questionId}-heading`}
    >
      <div className="mb-5 space-y-2">
        <h2 id={`${questionId}-heading`} className="text-xl font-semibold">
          Trợ lý tri thức
        </h2>
        <p className="max-w-prose text-sm leading-6 text-muted-foreground">
          Tham khảo trích dẫn từ nguồn bạn được phép xem. Hãy kiểm tra hướng dẫn trước khi áp dụng.
          Trợ lý không thay đổi ticket.
        </p>
      </div>

      <form onSubmit={(event) => void submit(event)} className="space-y-3">
        <label htmlFor={questionId} className="block font-medium">
          Bạn cần hỗ trợ điều gì?
        </label>
        <textarea
          id={questionId}
          aria-describedby={hintId}
          value={question}
          onChange={(event) => setQuestion(event.target.value)}
          maxLength={2000}
          rows={4}
          required
          disabled={pending}
          className="w-full rounded-lg border bg-background p-3 text-base leading-6 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-ring disabled:opacity-60"
        />
        <p id={hintId} className="text-sm text-muted-foreground">
          Tối đa 2.000 ký tự. Không gửi mật khẩu hoặc thông tin bí mật.
        </p>
        <button
          type="submit"
          disabled={pending || !question.trim()}
          className="min-h-11 rounded-lg bg-primary px-5 py-2 font-medium text-primary-foreground focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-ring disabled:opacity-50"
        >
          {pending ? "Đang tìm hướng dẫn…" : "Tìm hướng dẫn"}
        </button>
      </form>

      <div role="status" aria-atomic="true" className="mt-4 text-sm leading-6">
        {pending && "Đang kiểm tra nguồn tri thức và chuẩn bị trích dẫn."}
        {error && <p>{error}</p>}
        {result?.status === "INSUFFICIENT_EVIDENCE" && (
          <p>
            Chưa có đủ bằng chứng phù hợp. Bạn có thể bổ sung câu hỏi hoặc tiếp tục xử lý thủ công.
          </p>
        )}
      </div>

      {result?.status === "SUCCESS" && (
        <div className="mt-5 space-y-4 border-t pt-5">
          <p className="text-sm font-medium">
            Hướng dẫn tham khảo · Cần kiểm tra trước khi áp dụng
          </p>
          <blockquote className="whitespace-pre-wrap break-words border-l-2 pl-4 leading-7">
            {result.answer}
          </blockquote>
          <h3 className="font-medium">Nguồn trích dẫn</h3>
          <ul className="space-y-2 text-sm leading-6">
            {result.citations.map((citation) => (
              <li key={citation.citationId}>
                <span className="font-medium">
                  [{citation.citationId}] {citation.displayTitle}
                </span>
                <span className="text-muted-foreground"> · Phiên bản {citation.sourceVersion}</span>
              </li>
            ))}
          </ul>
        </div>
      )}
    </section>
  );
}
