import { useEffect, useRef } from "react";
import { useMutation } from "@tanstack/react-query";
import type { Assistance } from "./assistanceSchema";

/** Hàm app cung cấp để hỏi trợ lý về bản ghi đang xem. */
export type AskAssistance = (question: string, signal: AbortSignal) => Promise<Assistance>;

/**
 * Hỏi trợ lý tri thức. Câu hỏi mới hoặc rời trang thì huỷ request cũ,
 * vì sinh câu trả lời tốn tài nguyên backend; kết quả hiển thị luôn là của lần hỏi cuối.
 */
export const useAssistance = (ask: AskAssistance) => {
  const active = useRef<AbortController | null>(null);

  useEffect(() => () => active.current?.abort(), []);

  return useMutation({
    mutationFn: (question: string) => {
      active.current?.abort();
      const controller = new AbortController();
      active.current = controller;
      return ask(question, controller.signal);
    },
  });
};
