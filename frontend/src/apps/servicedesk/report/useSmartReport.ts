import { useRef, useState } from "react";
import { useQueryClient } from "@tanstack/react-query";
import { useCurrentWorkspace } from "../../../platform/human/workspace";
import { ApiError } from "../../../platform/shared/api";
import {
  appendClarification,
  evaluateEnrichment,
  getEnrichmentState,
  type EnrichmentState,
} from "../services/enrichmentService";
import { createTicket, type NewTicket, type TicketView } from "../services/ticketService";
import { nextQuestion, type Answers, type SlotKey } from "./reportSlots";
import {
  fromRule,
  MAX_QUESTIONS,
  pendingAiQuestion,
  smartAnswerLabel,
  type SmartQuestion,
} from "./smartQuestions";

export type SmartPhase = "describe" | "working" | "asking" | "done";

export interface SmartLine {
  from: "bot" | "user";
  text: string;
}

export const GREETING = "Bạn đang gặp vấn đề gì? Nhập tiêu đề và mô tả, mình sẽ hỏi thêm nếu còn thiếu.";

// AI đánh giá xong thường trong vài giây; quá thì thôi hỏi, ticket đã gửi vẫn được xử lý
const SETTLE_ATTEMPTS = 8;
const SETTLE_WAIT_MS = 1500;

interface Session {
  ticket: TicketView;
  /** ai: backend bật enrichment (B); rules: luật FE (A). */
  mode: "ai" | "rules";
  problem: string;
  revision: number;
  answers: Answers;
  skipped: string[];
  asked: number;
  state: EnrichmentState | null;
}

const wait = (ms: number) => new Promise((resolve) => setTimeout(resolve, ms));

/**
 * Gửi yêu cầu kiểu trợ lý: tạo ticket ngay ở lần gửi đầu, rồi hỏi từng câu còn thiếu.
 * Nguồn câu hỏi: AI backend (enrichment) nếu được bật, không thì luật ở FE. Đủ thông tin
 * (hoặc hết câu, hoặc AI không phản hồi kịp) thì gọi onFinished để form tự đóng.
 */
export const useSmartReport = ({
  onSent,
  onFinished,
}: {
  /** Đã tạo ticket (câu hỏi thêm có thể còn). */
  onSent: (ticket: TicketView) => void;
  onFinished: (ticket: TicketView) => void;
}) => {
  const { current } = useCurrentWorkspace();
  const queryClient = useQueryClient();
  // Một mã cho mỗi lần mở form: gửi lại sau lỗi mạng không tạo ticket trùng (R0)
  const [clientRequestId] = useState(() => crypto.randomUUID());
  const [phase, setPhase] = useState<SmartPhase>("describe");
  const [lines, setLines] = useState<SmartLine[]>([{ from: "bot", text: GREETING }]);
  const [question, setQuestion] = useState<SmartQuestion | null>(null);
  const [error, setError] = useState<unknown>(null);
  const [ticket, setTicket] = useState<TicketView | null>(null);
  const session = useRef<Session | null>(null);

  const say = (line: SmartLine) => setLines((previous) => [...previous, line]);

  const finish = () => {
    const sent = session.current?.ticket;
    setPhase("done");
    if (sent) onFinished(sent);
  };

  /** Chờ AI đánh giá xong lượt hiện tại (gọi đánh giá khi mới có thông tin, chờ khi đang chạy). */
  const settle = async (workspace: string, id: string, state: EnrichmentState | null) => {
    let latest = state;
    for (let attempt = 0; latest && attempt < SETTLE_ATTEMPTS; attempt++) {
      if (latest.manualReviewRequired) return latest;
      if (latest.status === "NEW" || latest.status === "REASSESS_QUEUED") {
        latest = await evaluateEnrichment(workspace, id);
      } else if (latest.status === "EVALUATING") {
        await wait(SETTLE_WAIT_MS);
        latest = await getEnrichmentState(workspace, id);
      } else return latest;
    }
    return null;
  };

  /** Chọn câu hỏi tiếp theo; không còn thì xong. */
  const advance = async (workspace: string) => {
    const s = session.current;
    if (!s) return;
    let next: SmartQuestion | null = null;
    if (s.asked < MAX_QUESTIONS) {
      if (s.mode === "rules") {
        const rule = nextQuestion(s.problem, s.answers);
        next = rule ? fromRule(rule) : null;
      } else {
        s.state = await settle(workspace, s.ticket.id, s.state).catch(() => null);
        if (s.state) s.revision = s.state.contextRevision;
        next = pendingAiQuestion(s.state, s.skipped);
      }
    }
    if (!next) {
      finish();
      return;
    }
    setQuestion(next);
    say({ from: "bot", text: next.text });
    setPhase("asking");
  };

  const send = async (input: Omit<NewTicket, "clientRequestId">) => {
    if (!current) return;
    setError(null);
    setPhase("working");
    say({ from: "user", text: `${input.title}\n${input.description}` });
    try {
      const created = await createTicket(current, { clientRequestId, ...input });
      setTicket(created);
      onSent(created);
      void queryClient.invalidateQueries({ queryKey: ["servicedesk", "tickets", current] });
      // 404 hoặc lỗi: backend chưa bật enrichment → dùng luật FE
      const state = await getEnrichmentState(current, created.id).catch(() => null);
      session.current = {
        ticket: created,
        mode: state ? "ai" : "rules",
        problem: `${input.title}\n${input.description}`,
        revision: state?.contextRevision ?? 0,
        answers: {},
        skipped: [],
        asked: 0,
        state,
      };
      await advance(current);
    } catch (failure) {
      // Chưa tạo được ticket: gỡ câu vừa hiện, giữ nội dung trong ô để gửi lại
      if (!session.current) {
        setLines((previous) => previous.slice(0, -1));
        setError(failure);
        setPhase("describe");
        return;
      }
      finish();
    }
  };

  /** value null: bỏ qua câu đang hỏi. */
  const answer = async (value: string | null) => {
    const s = session.current;
    if (!current || !s || !question) return;
    setPhase("working");
    say({ from: "user", text: smartAnswerLabel(question, value) });
    s.asked += 1;
    try {
      if (value === null) {
        s.skipped.push(question.slot);
        if (s.mode === "rules") s.answers[question.slot as SlotKey] = null;
      } else {
        if (s.mode === "rules") s.answers[question.slot as SlotKey] = value;
        // Gửi cả câu gõ tự do (vd "phòng thầy Nam"): backend nhận 1..256 ký tự, tự chuẩn hóa sau,
        // điều phối viên vẫn đọc được
        const saved = await appendClarification(current, s.ticket.id, {
          contractVersion: "ticket-clarification@0.1.0",
          clientAnswerId: crypto.randomUUID(),
          expectedContextRevision: s.revision,
          answers: [{ slot: question.slot, value, correction: question.correction }],
        });
        s.revision = saved.contextRevision;
        if (s.mode === "ai") s.state = await getEnrichmentState(current, s.ticket.id);
      }
      await advance(current);
    } catch (failure) {
      // Người khác vừa bổ sung (409): đọc lại rồi hỏi tiếp; lỗi khác thì dừng hỏi, ticket đã gửi
      if (failure instanceof ApiError && failure.status === 409 && s.mode === "ai") {
        s.state = await getEnrichmentState(current, s.ticket.id).catch(() => null);
        await advance(current);
        return;
      }
      finish();
    }
  };

  return {
    phase,
    lines,
    question,
    error,
    /** Đã tạo ticket: đóng form giữa chừng vẫn coi là đã gửi. */
    ticket,
    send,
    answer,
    finish,
  };
};
