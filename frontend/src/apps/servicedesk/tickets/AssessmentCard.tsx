import { IconAlertTriangle, IconQuestionMark, IconSparkles } from "@tabler/icons-react";
import { PriorityTag } from "../../../platform/process";
import { FieldRow } from "../../../platform/shared/ui/FieldRow";
import { TextSkeleton } from "../../../platform/shared/ui/Skeleton";
import { Tag } from "../../../platform/shared/ui/Tag";
import type { Assessment } from "../services/ticketService";
import {
  CATEGORY_LABELS,
  EVIDENCE_FIELD_LABELS,
  groupLabel,
  IMPACT_LABELS,
  URGENCY_LABELS,
} from "./ticketLabels";

const Header = ({ needsReview }: { needsReview: boolean }) => (
  <h2 className="mb-2 flex items-center gap-1.5 font-medium">
    <IconSparkles size={16} stroke={2} className="text-crm-accent" />
    Gợi ý của AI
    {needsReview && <Tag tone="orange">Cần kiểm tra</Tag>}
  </h2>
);

/**
 * Kết quả AI đã lưu lúc tạo ticket (plan mục 2): chỉ để tham khảo, điều phối viên quyết.
 * Không có độ tin cậy; AI không chắc thì không đưa loại lỗi mà đưa câu hỏi gợi ý.
 */
export const AssessmentCard = ({ assessment }: { assessment: Assessment }) => {
  if (assessment.status === "PENDING") {
    return (
      <section className="border-b border-crm-line px-4 py-4">
        <Header needsReview={false} />
        <p className="mb-2 text-crm-sm text-crm-text-3">AI đang phân tích yêu cầu...</p>
        <TextSkeleton lines={3} label="AI đang phân tích" />
      </section>
    );
  }

  if (assessment.status === "DEGRADED") {
    return (
      <section className="border-b border-crm-line px-4 py-4">
        <Header needsReview />
        <p className="flex items-center gap-1.5 text-crm-text-2">
          <IconAlertTriangle size={14} stroke={2} className="text-crm-tone-orange" />
          AI tạm không khả dụng, điều phối viên chọn thủ công.
        </p>
      </section>
    );
  }

  const { category, impact, urgency, priority, suggestedGroup } = assessment;
  return (
    <section className="border-b border-crm-line px-4 py-4">
      <Header needsReview={assessment.needsReview} />
      {category ? (
        <dl>
          <FieldRow label="Loại sự cố">{CATEGORY_LABELS[category]}</FieldRow>
          {impact && <FieldRow label="Ảnh hưởng">{IMPACT_LABELS[impact]}</FieldRow>}
          {urgency && <FieldRow label="Độ gấp">{URGENCY_LABELS[urgency]}</FieldRow>}
          {priority && (
            <FieldRow label="Ưu tiên">
              <PriorityTag priority={priority} />
            </FieldRow>
          )}
          {suggestedGroup && <FieldRow label="Tổ gợi ý">{groupLabel(suggestedGroup)}</FieldRow>}
        </dl>
      ) : (
        <p className="text-crm-text-2">AI chưa đủ thông tin để phân loại.</p>
      )}

      {assessment.explanation && (
        <div className="mt-2 flex flex-col gap-1 text-crm-sm text-crm-text-2">
          <p>{assessment.explanation.classificationReason}</p>
          <p>{assessment.explanation.priorityReason}</p>
        </div>
      )}

      {assessment.evidence && assessment.evidence.length > 0 && (
        <ul className="mt-2 flex flex-col gap-1">
          {assessment.evidence.map((item, index) => (
            <li key={`${item.field}-${index}`} className="text-crm-sm">
              <span className="text-crm-text-3">{EVIDENCE_FIELD_LABELS[item.field]}: </span>
              {/* Trích nguyên văn nội dung người dùng nhập: hiển thị dạng chữ thuần */}
              <q className="text-crm-text">{item.text}</q>
            </li>
          ))}
        </ul>
      )}

      {assessment.nextQuestions.length > 0 && (
        <div className="mt-3 rounded-crm-sm bg-crm-subtle px-3 py-2">
          <p className="mb-1 flex items-center gap-1 text-crm-sm font-medium text-crm-text-2">
            <IconQuestionMark size={14} stroke={2} />
            Nên hỏi thêm người yêu cầu
          </p>
          <ul className="list-disc pl-5 text-crm-sm text-crm-text">
            {assessment.nextQuestions.map((question) => (
              <li key={question}>{question}</li>
            ))}
          </ul>
        </div>
      )}
    </section>
  );
};
