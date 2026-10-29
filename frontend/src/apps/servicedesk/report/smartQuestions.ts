import type { EnrichmentQuestion, EnrichmentState } from "../services/enrichmentService";
import { ENRICHMENT_FIELDS } from "../tickets/enrichmentFields";
import { normalizeAnswer, QUESTIONS, type Choice, type SlotKey, type SlotQuestion } from "./reportSlots";

/** Tối đa câu hỏi thêm cho một lần gửi, để người dùng không bị hỏi dồn. */
export const MAX_QUESTIONS = 3;

/** Một câu hỏi thêm, dù do luật FE (A) hay AI backend (B) đặt ra. */
export interface SmartQuestion {
  slot: string;
  text: string;
  /** Có thì bấm chọn; giá trị gửi backend nằm ở value. */
  choices?: readonly Choice[];
  placeholder?: string;
  /** AI thấy thông tin mâu thuẫn với lần trước: câu trả lời là đính chính. */
  correction: boolean;
}

export const fromRule = (question: SlotQuestion): SmartQuestion => ({
  slot: question.slot,
  text: question.text,
  choices: question.choices,
  placeholder: question.placeholder,
  correction: false,
});

/** Câu hỏi AI không kèm lựa chọn (backend trả rỗng): lấy lựa chọn theo mục từ enrichmentFields. */
export const fromAi = (question: EnrichmentQuestion): SmartQuestion => {
  const field = ENRICHMENT_FIELDS[question.slot];
  const choices =
    question.choices.length > 0
      ? question.choices.map((choice) => ({ label: choice, value: choice }))
      : field?.choices?.map(([value, label]) => ({ label, value }));
  return {
    slot: question.slot,
    text: question.text,
    choices,
    placeholder: choices ? undefined : field?.label,
    correction: question.correctionRequired,
  };
};

/** Câu AI còn hỏi (bỏ câu người dùng đã bỏ qua); không còn thì đã đủ để gửi đi xét. */
export const pendingAiQuestion = (state: EnrichmentState | null, skipped: readonly string[]) => {
  if (!state || state.manualReviewRequired || state.status !== "AWAITING_ANSWER") return null;
  const question = state.questions.find((item) => !skipped.includes(item.slot));
  return question ? fromAi(question) : null;
};

/** Câu đã gõ → giá trị lưu; phòng / số máy chuẩn hóa như luật FE, mục khác giữ nguyên. */
export const normalizeSmartAnswer = (slot: string, raw: string) =>
  slot in QUESTIONS ? normalizeAnswer(slot as SlotKey, raw) : raw.trim();

/** Chữ hiển thị của câu trả lời trong khung chat. */
export const smartAnswerLabel = (question: SmartQuestion, value: string | null) =>
  value === null
    ? "Bỏ qua"
    : (question.choices?.find((choice) => choice.value === value)?.label ?? value);
