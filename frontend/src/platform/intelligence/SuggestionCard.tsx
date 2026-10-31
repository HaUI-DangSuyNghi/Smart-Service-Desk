import { useId } from "react";
import { IconSparkles } from "@tabler/icons-react";
import { Button } from "@/platform/shared/ui/button";
import { FieldRow } from "@/platform/shared/ui/FieldRow";
import { Tag } from "@/platform/shared/ui/Tag";
import { aiErrorMessage, aiErrorRequestId } from "./aiErrors";
import { useSuggestion, type FetchSuggestion } from "./useSuggestion";

interface Props {
  fetchSuggestion: FetchSuggestion;
  /** Nhãn tiếng Việt cho từng trường kết quả, theo profile của app; trường thiếu nhãn hiện tên gốc. */
  fieldLabels: Readonly<Record<string, string>>;
  /** Nhãn cho trường đầu vào mà bằng chứng trích từ đó (title, description...). */
  evidenceLabels?: Readonly<Record<string, string>>;
}

/** Gợi ý phân loại của AI kèm bằng chứng và giải thích; chỉ để tham khảo, không tự áp dụng. */
export const SuggestionCard = ({ fetchSuggestion, fieldLabels, evidenceLabels = {} }: Props) => {
  const headingId = useId();
  const suggestion = useSuggestion(fetchSuggestion);
  const data = suggestion.data;
  const requestId = aiErrorRequestId(suggestion.error);

  return (
    <section className="border-b border-crm-line px-4 py-4" aria-labelledby={headingId}>
      <div className="mb-3 flex flex-wrap items-center justify-between gap-2">
        <h2 id={headingId} className="font-medium">
          Gợi ý của AI
        </h2>
        <Button
          variant="outline"
          size="sm"
          className="h-9 md:h-7"
          disabled={suggestion.isPending}
          onClick={() => suggestion.mutate()}
        >
          <IconSparkles size={14} stroke={2} />
          {suggestion.isPending ? "Đang phân tích..." : data ? "Gợi ý lại" : "Gợi ý phân loại"}
        </Button>
      </div>

      {!data && !suggestion.error && !suggestion.isPending && (
        <p className="text-crm-sm text-crm-text-3">
          AI đọc nội dung và gợi ý cách phân loại. Bạn quyết định có áp dụng hay không.
        </p>
      )}

      {suggestion.error && (
        <div className="text-crm-sm">
          <p>{aiErrorMessage(suggestion.error)}</p>
          {requestId && <p className="text-crm-text-3">Mã yêu cầu: {requestId}</p>}
        </div>
      )}

      {data?.status === "DEGRADED" && (
        <p className="text-crm-sm">AI tạm không khả dụng. Bạn vẫn xử lý thủ công như bình thường.</p>
      )}

      {data?.status === "SUCCESS" && data.result && (
        <div className="space-y-3">
          {data.needsReview && <Tag tone="orange">Cần người kiểm tra</Tag>}
          <dl>
            {Object.entries(data.result).map(([key, value]) => (
              <FieldRow key={key} label={fieldLabels[key] ?? key}>
                {value}
              </FieldRow>
            ))}
          </dl>
          {data.explanation && (
            <p className="text-crm-sm wrap-break-word text-crm-text-2">{data.explanation.text}</p>
          )}
          {data.evidence.length > 0 && (
            <div>
              <h3 className="mb-1 text-crm-sm font-medium">Căn cứ</h3>
              <ul className="space-y-1 text-crm-sm">
                {data.evidence.map((item, index) => (
                  <li key={index} className="wrap-break-word">
                    <span className="text-crm-text-3">
                      {evidenceLabels[item.field] ?? item.field}:{" "}
                    </span>
                    “{item.text}”
                  </li>
                ))}
              </ul>
            </div>
          )}
          {data.runtime && (
            <p className="text-crm-sm text-crm-text-3">Phiên bản: {data.runtime.profileVersion}</p>
          )}
        </div>
      )}
    </section>
  );
};
