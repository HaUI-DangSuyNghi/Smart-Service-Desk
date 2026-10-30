import { IconAlertTriangle, IconQuestionMark, IconSparkles } from "@tabler/icons-react";
import { PriorityTag } from "../../../platform/process";
import { FieldRow } from "../../../platform/shared/ui/FieldRow";
import { TextSkeleton } from "../../../platform/shared/ui/Skeleton";
import { Tag } from "../../../platform/shared/ui/Tag";
import type { Intake } from "../services/intakeService";
import type { Assessment } from "../services/ticketService";
import { Button } from "../../../platform/shared/ui/button";
import type { ReactNode } from "react";
import {
  CATEGORY_LABELS,
  EVIDENCE_FIELD_LABELS,
  IMPACT_LABELS,
  URGENCY_LABELS,
} from "./ticketLabels";
import { DEFAULT_PRIORITY, intakeSuggestion } from "./reviewForm";
import type { Category } from "./ticketRules";

const Header = ({ needsReview, model = false }: { needsReview: boolean; model?: boolean }) => (
  <h2 className="mb-2 flex items-center gap-1.5 font-medium">
    <IconSparkles size={16} stroke={2} className="text-crm-accent" />
    Gợi ý của AI
    {model && <span className="text-crm-sm font-normal text-crm-text-3">Model + bộ luật</span>}
    {needsReview && <Tag tone="orange">Cần kiểm tra</Tag>}
  </h2>
);

const Notice = ({ children, action }: { children: string; action?: ReactNode }) => (
  <section className="border-b border-crm-line px-4 py-4">
    <Header needsReview />
    <p className="flex items-center gap-1.5 text-crm-text-2">
      <IconAlertTriangle size={14} stroke={2} className="shrink-0 text-crm-tone-orange" />
      {children}
    </p>
    {action}
  </section>
);

const NONE = <span className="text-crm-text-3">—</span>;
const UNKNOWN = <span className="text-crm-text-3">Chưa rõ</span>;

/** Kết quả từng nguồn: loại, hoặc trạng thái ngắn khi không có loại. */
const sourceLabel = (category: Category | null, status: string) =>
  category
    ? CATEGORY_LABELS[category]
    : status === "UNAVAILABLE"
      ? "Tạm không khả dụng"
      : "Chưa kết luận";

/**
 * Kết quả AI chạy nền lúc tạo ticket: chỉ để tham khảo, điều phối viên quyết.
 * Không có độ tin cậy; AI không đủ thông tin thì không đưa loại lỗi mà đưa câu hỏi gợi ý.
 */
export interface AssessmentCardProps {
  assessment: Assessment;
  modelEnabled?: boolean;
  onRetry?: () => void;
  retrying?: boolean;
  retryError?: boolean;
}

export const AssessmentCard = ({
  assessment,
  modelEnabled = false,
  onRetry,
  retrying = false,
  retryError = false,
}: AssessmentCardProps) => {
  const retry = onRetry && (
    <div className="mt-3">
      <Button
        variant="outline"
        size="sm"
        disabled={retrying}
        onClick={onRetry}
        aria-busy={retrying}
      >
        {retrying
          ? "Đang gửi yêu cầu..."
          : modelEnabled
            ? "Phân tích lại bằng model"
            : "Thử phân tích lại"}
      </Button>
      {retryError && (
        <p role="alert" className="mt-2 text-crm-sm text-crm-tone-orange">
          Chưa gửi được yêu cầu. Hãy tải lại ticket và thử lại.
        </p>
      )}
    </div>
  );
  if (assessment.status === "PENDING") {
    return (
      <section className="border-b border-crm-line px-4 py-4">
        <Header needsReview={false} />
        <p role="status" aria-atomic="true" className="mb-2 text-crm-sm text-crm-text-3">
          {/* errorCode khi PENDING: lần trước lỗi, đang chờ thử lại */}
          {assessment.errorCode ? "AI đang thử phân tích lại..." : "AI đang phân tích yêu cầu..."}
        </p>
        <TextSkeleton lines={3} label="AI đang phân tích" />
      </section>
    );
  }

  if (assessment.status === "DEGRADED" || !assessment.result) {
    const errorMessage =
      assessment.errorCode === "AI_WORKSPACE_NOT_ENABLED"
        ? modelEnabled
          ? "Ticket chưa được phân tích khi AI bị giới hạn ở không gian khác. Bạn có thể yêu cầu phân tích lại."
          : "AI chưa được bật cho không gian này. Điều phối viên vẫn có thể xử lý thủ công."
        : assessment.errorCode === "ASSESSMENT_NOT_QUEUED"
          ? "Ticket chưa có kết quả AI. Bạn có thể yêu cầu model phân tích."
          : assessment.errorCode === "INTELLIGENCE_INTEGRATION_FAILED"
            ? "Kết nối AI chưa được cấu hình đúng. Vui lòng liên hệ người quản trị."
            : assessment.errorCode === "AUTHORIZATION_REVOKED"
              ? "AI chưa được phép xử lý ticket trong không gian này."
              : assessment.errorCode === "INTELLIGENCE_CONTRACT_FAILED"
                ? "Kết quả AI không hợp lệ. Điều phối viên có thể xử lý thủ công."
                : "AI tạm không khả dụng, điều phối viên chọn thủ công.";
    return (
      <Notice
        action={
          assessment.errorCode === "USE_FINAL_CONTEXT_ASSESSMENT" ||
          (assessment.errorCode === "AI_WORKSPACE_NOT_ENABLED" && !modelEnabled)
            ? undefined
            : retry
        }
      >
        {assessment.errorCode === "USE_FINAL_CONTEXT_ASSESSMENT"
          ? "Ticket qua bước tiếp nhận: xem kết quả AI ở khối Tiếp nhận."
          : errorMessage}
      </Notice>
    );
  }

  const result = assessment.result;
  // INSUFFICIENT_INFORMATION: OTHER/MEDIUM chỉ là mặc định, không hiện như một gợi ý
  const classified = result.reasonCode !== "INSUFFICIENT_INFORMATION" && result.category;
  const cooperation = result.cooperation;
  // Gọn: loại, ưu tiên (đã gán thành nhãn ticket), nguồn, rồi bằng chứng và câu nên hỏi thêm
  return (
    <section className="border-b border-crm-line px-4 py-4">
      <Header
        needsReview={result.needsReview}
        model={result.engine === "model" || result.engine === "hybrid"}
      />
      <dl>
        <FieldRow label="Loại sự cố">
          <span className="flex flex-wrap items-center gap-1.5">
            {classified && result.category ? CATEGORY_LABELS[result.category] : NONE}
            {(cooperation?.decision === "CONFLICT" ||
              result.reasonCode === "ROOT_CAUSE_CONFLICT") && <Tag tone="orange">Mâu thuẫn</Tag>}
          </span>
        </FieldRow>
        <FieldRow label="Ưu tiên">
          {classified && result.priority ? <PriorityTag priority={result.priority} /> : NONE}
        </FieldRow>
        {(result.impact || result.urgency) && (
          <>
            <FieldRow label="Ảnh hưởng">
              {result.impact ? IMPACT_LABELS[result.impact] : UNKNOWN}
            </FieldRow>
            <FieldRow label="Độ gấp">
              {result.urgency ? URGENCY_LABELS[result.urgency] : UNKNOWN}
            </FieldRow>
          </>
        )}
        {cooperation && (
          <FieldRow label={`${cooperation.provider} · Bộ luật`}>
            {sourceLabel(cooperation.modelCategory, cooperation.modelStatus)} ·{" "}
            {sourceLabel(cooperation.rulesCategory, cooperation.rulesStatus)}
          </FieldRow>
        )}
      </dl>

      {result.evidence.length > 0 && (
        <p className="mt-2 text-crm-sm">
          <span className="text-crm-text-3">Bằng chứng: </span>
          {result.evidence.map((item, index) => (
            <span key={`${item.field}-${index}`}>
              {index > 0 && ", "}
              {/* Trích nguyên văn nội dung người dùng nhập: hiển thị dạng chữ thuần */}
              <q title={EVIDENCE_FIELD_LABELS[item.field]}>{item.text}</q>
            </span>
          ))}
        </p>
      )}

      {result.nextQuestions.length > 0 && (
        <div className="mt-2 text-crm-sm">
          <p className="flex items-center gap-1 text-crm-text-3">
            <IconQuestionMark size={14} stroke={2} />
            Nên hỏi thêm
          </p>
          <ul className="list-disc pl-5 text-crm-text">
            {result.nextQuestions.map((question) => (
              <li key={question}>{question}</li>
            ))}
          </ul>
        </div>
      )}
      {modelEnabled &&
        (result.engine === "rules" || result.cooperation?.modelStatus === "UNAVAILABLE") &&
        retry}
    </section>
  );
};

// Tiếp nhận còn chạy: AI chưa có kết quả cuối
const INTAKE_WAITING: Partial<Record<Intake["status"], string>> = {
  NEW: "AI đang kiểm tra thông tin...",
  NEEDS_ENRICHMENT: "Đang chờ người gửi bổ sung thông tin.",
  DUPLICATE_HOLD: "Đang kiểm tra ticket trùng.",
  FINAL_ASSESSMENT_QUEUED: "AI đang phân tích yêu cầu...",
  ASSESSING: "AI đang phân tích yêu cầu...",
};

/** Ticket qua tiếp nhận: kết quả AI là đề xuất cuối (tính cả thông tin người gửi bổ sung). */
export const IntakeAssessmentCard = ({ intake }: { intake: Intake }) => {
  const suggestion = intakeSuggestion(intake.finalAssessment?.ai07);
  const waiting = INTAKE_WAITING[intake.status];
  return (
    <section className="border-b border-crm-line px-4 py-4">
      <Header needsReview={!waiting} />
      {waiting ? (
        <p role="status" className="text-crm-sm text-crm-text-3">
          {waiting}
        </p>
      ) : suggestion ? (
        <dl>
          <FieldRow label="Loại sự cố">
            {suggestion.category ? CATEGORY_LABELS[suggestion.category] : NONE}
          </FieldRow>
          {/* Không tính được ưu tiên thì hiện mức mặc định, giống nhãn backend gán vào ticket */}
          <FieldRow label="Ưu tiên">
            <span className="flex flex-wrap items-center gap-1.5">
              <PriorityTag priority={suggestion.priority ?? DEFAULT_PRIORITY} />
              {!suggestion.priority && <Tag tone="gray">Mặc định</Tag>}
            </span>
          </FieldRow>
          {(suggestion.impact || suggestion.urgency) && (
            <>
              <FieldRow label="Ảnh hưởng">
                {suggestion.impact ? IMPACT_LABELS[suggestion.impact] : UNKNOWN}
              </FieldRow>
              <FieldRow label="Độ gấp">
                {suggestion.urgency ? URGENCY_LABELS[suggestion.urgency] : UNKNOWN}
              </FieldRow>
            </>
          )}
        </dl>
      ) : (
        <p className="flex items-center gap-1.5 text-crm-text-2">
          <IconAlertTriangle size={14} stroke={2} className="shrink-0 text-crm-tone-orange" />
          AI chưa đưa ra được đề xuất, điều phối viên phân loại thủ công.
        </p>
      )}
    </section>
  );
};
