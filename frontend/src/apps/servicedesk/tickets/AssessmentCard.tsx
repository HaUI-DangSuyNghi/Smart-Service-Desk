import { IconAlertTriangle, IconQuestionMark, IconSparkles } from "@tabler/icons-react";
import { PriorityTag } from "../../../platform/process";
import { FieldRow } from "../../../platform/shared/ui/FieldRow";
import { TextSkeleton } from "../../../platform/shared/ui/Skeleton";
import { Tag } from "../../../platform/shared/ui/Tag";
import type { Assessment } from "../services/ticketService";
import { CATEGORY_LABELS, EVIDENCE_FIELD_LABELS, IMPACT_LABELS, URGENCY_LABELS } from "./ticketLabels";

const Header = ({ needsReview }: { needsReview: boolean }) => (
  <h2 className="mb-2 flex items-center gap-1.5 font-medium">
    <IconSparkles size={16} stroke={2} className="text-crm-accent" />
    Gợi ý của AI
    {needsReview && <Tag tone="orange">Cần kiểm tra</Tag>}
  </h2>
);

const Notice = ({ children }: { children: string }) => (
  <section className="border-b border-crm-line px-4 py-4">
    <Header needsReview />
    <p className="flex items-center gap-1.5 text-crm-text-2">
      <IconAlertTriangle size={14} stroke={2} className="shrink-0 text-crm-tone-orange" />
      {children}
    </p>
  </section>
);

/**
 * Kết quả AI chạy nền lúc tạo ticket: chỉ để tham khảo, điều phối viên quyết.
 * Không có độ tin cậy; AI không đủ thông tin thì không đưa loại lỗi mà đưa câu hỏi gợi ý.
 */
export const AssessmentCard = ({ assessment }: { assessment: Assessment }) => {
  if (assessment.status === "PENDING") {
    return (
      <section className="border-b border-crm-line px-4 py-4">
        <Header needsReview={false} />
        <p className="mb-2 text-crm-sm text-crm-text-3">
          {/* errorCode khi PENDING: lần trước lỗi, đang chờ thử lại */}
          {assessment.errorCode ? "AI đang thử phân tích lại..." : "AI đang phân tích yêu cầu..."}
        </p>
        <TextSkeleton lines={3} label="AI đang phân tích" />
      </section>
    );
  }

  if (assessment.status === "DEGRADED" || !assessment.result) {
    return (
      <Notice>
        {assessment.errorCode === "USE_FINAL_CONTEXT_ASSESSMENT"
          ? "Ticket qua bước tiếp nhận: xem kết quả AI ở khối Tiếp nhận."
          : "AI tạm không khả dụng, điều phối viên chọn thủ công."}
      </Notice>
    );
  }

  const result = assessment.result;
  // INSUFFICIENT_INFORMATION: OTHER/MEDIUM chỉ là mặc định, không hiện như một gợi ý
  const classified = result.reasonCode !== "INSUFFICIENT_INFORMATION" && result.category;
  return (
    <section className="border-b border-crm-line px-4 py-4">
      <Header needsReview={result.needsReview} />
      {classified && result.category ? (
        <dl>
          <FieldRow label="Loại sự cố">{CATEGORY_LABELS[result.category]}</FieldRow>
          {result.impact && <FieldRow label="Ảnh hưởng">{IMPACT_LABELS[result.impact]}</FieldRow>}
          {result.urgency && <FieldRow label="Độ gấp">{URGENCY_LABELS[result.urgency]}</FieldRow>}
          {result.priority && (
            <FieldRow label="Ưu tiên">
              <PriorityTag priority={result.priority} />
            </FieldRow>
          )}
        </dl>
      ) : (
        <p className="text-crm-text-2">AI chưa đủ thông tin để phân loại.</p>
      )}
      {result.reasonCode === "ROOT_CAUSE_CONFLICT" && (
        <p className="mt-1 text-crm-sm text-crm-tone-orange">
          Nội dung có dấu hiệu của nhiều loại sự cố, nên kiểm tra kỹ.
        </p>
      )}

      {result.explanation && (
        <div className="mt-2 flex flex-col gap-1 text-crm-sm text-crm-text-2">
          <p>{result.explanation.classificationReason}</p>
          <p>{result.explanation.priorityReason}</p>
        </div>
      )}

      {result.evidence.length > 0 && (
        <ul className="mt-2 flex flex-col gap-1">
          {result.evidence.map((item, index) => (
            <li key={`${item.field}-${index}`} className="text-crm-sm">
              <span className="text-crm-text-3">{EVIDENCE_FIELD_LABELS[item.field]}: </span>
              {/* Trích nguyên văn nội dung người dùng nhập: hiển thị dạng chữ thuần */}
              <q className="text-crm-text">{item.text}</q>
            </li>
          ))}
        </ul>
      )}

      {result.nextQuestions.length > 0 && (
        <div className="mt-3 rounded-crm-sm bg-crm-subtle px-3 py-2">
          <p className="mb-1 flex items-center gap-1 text-crm-sm font-medium text-crm-text-2">
            <IconQuestionMark size={14} stroke={2} />
            Nên hỏi thêm người yêu cầu
          </p>
          <ul className="list-disc pl-5 text-crm-sm text-crm-text">
            {result.nextQuestions.map((question) => (
              <li key={question}>{question}</li>
            ))}
          </ul>
        </div>
      )}
    </section>
  );
};
