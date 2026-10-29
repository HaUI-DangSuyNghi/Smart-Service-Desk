import { useState, type FormEvent } from "react";
import { IconSend } from "@tabler/icons-react";
import { Button } from "../../../platform/shared/ui/button";
import { Input } from "../../../platform/shared/ui/input";
import { normalizeSmartAnswer, type SmartQuestion } from "./smartQuestions";

// Khớp giới hạn một câu trả lời của backend (clarification value ≤ 256)
const MAX_ANSWER = 256;

interface Props {
  question: SmartQuestion;
  /** Đang gửi: khóa nút để không trả lời hai lần. */
  disabled: boolean;
  onAnswer: (value: string | null) => void;
}

/** Trả lời một câu hỏi thêm: bấm lựa chọn, hoặc gõ vào một ô rồi Gửi; luôn bỏ qua được. */
export const AnswerInput = ({ question, disabled, onAnswer }: Props) => {
  const [text, setText] = useState("");
  const trimmed = text.trim();

  const handleSubmit = (event: FormEvent) => {
    event.preventDefault();
    if (trimmed) onAnswer(normalizeSmartAnswer(question.slot, trimmed));
  };

  return (
    <div className="flex flex-col gap-2">
      {question.choices ? (
        <div className="flex flex-wrap gap-2">
          {question.choices.map((choice) => (
            <Button
              key={choice.value}
              type="button"
              variant="outline"
              size="sm"
              className="h-9 md:h-7"
              disabled={disabled}
              onClick={() => onAnswer(choice.value)}
            >
              {choice.label}
            </Button>
          ))}
        </div>
      ) : (
        <form onSubmit={handleSubmit} className="flex items-center gap-2">
          <Input
            autoFocus
            value={text}
            onChange={(event) => setText(event.target.value)}
            maxLength={MAX_ANSWER}
            placeholder={question.placeholder ?? "Nhập câu trả lời"}
            aria-label={question.text}
            disabled={disabled}
            className="min-w-0 flex-1"
          />
          <Button
            type="submit"
            size="icon"
            aria-label="Gửi"
            disabled={disabled || !trimmed}
            className="size-9 shrink-0 md:size-8"
          >
            <IconSend size={16} stroke={2} />
          </Button>
        </form>
      )}
      <button
        type="button"
        disabled={disabled}
        onClick={() => onAnswer(null)}
        className="self-start text-crm-sm text-crm-text-3 underline-offset-2 hover:text-crm-text hover:underline disabled:pointer-events-none"
      >
        Bỏ qua câu này
      </button>
    </div>
  );
};
