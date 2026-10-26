import { useMutation } from "@tanstack/react-query";
import type { Suggestion } from "./suggestionSchema";

/** Hàm app cung cấp để lấy gợi ý cho bản ghi đang xem. */
export type FetchSuggestion = () => Promise<Suggestion>;

/** Gợi ý chỉ là tạm thời, không cache: mỗi lần bấm hỏi lại AI. */
export const useSuggestion = (fetchSuggestion: FetchSuggestion) =>
  useMutation({ mutationFn: fetchSuggestion });
