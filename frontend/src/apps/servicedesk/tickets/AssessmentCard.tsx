import { IconAlertTriangle, IconQuestionMark, IconSparkles } from "@tabler/icons-react";
import { PriorityTag } from "../../../platform/process";
import { FieldRow } from "../../../platform/shared/ui/FieldRow";
import { TextSkeleton } from "../../../platform/shared/ui/Skeleton";
import { Tag } from "../../../platform/shared/ui/Tag";
import type { Assessment } from "../services/ticketService";
import { Button } from "../../../platform/shared/ui/button";
import type { ReactNode } from "react";
import {
  CATEGORY_LABELS,
  EVIDENCE_FIELD_LABELS,
  IMPACT_LABELS,
  URGENCY_LABELS,
} from "./ticketLabels";

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
  return (
    <section className="border-b border-crm-line px-4 py-4">
      <Header
        needsReview={result.needsReview}
        model={result.engine === "model" || result.engine === "hybrid"}
      />
      {result.cooperation && (
        <div className="mb-3 text-crm-sm text-crm-text-2">
          <p>
            Model ({result.cooperation.provider}):{" "}
            {result.cooperation.modelCategory
              ? CATEGORY_LABELS[result.cooperation.modelCategory]
              : result.cooperation.modelStatus === "UNAVAILABLE"
                ? "Tạm không khả dụng"
                : "Chưa kết luận"}
            .
          </p>
          <p>
            Bộ luật:{" "}
            {result.cooperation.rulesCategory
              ? CATEGORY_LABELS[result.cooperation.rulesCategory]
              : result.cooperation.rulesStatus === "UNAVAILABLE"
                ? "Tạm không khả dụng"
                : "Chưa đủ dữ kiện kết luận đầy đủ"}
            .
          </p>
          {result.cooperation.decision === "CONFLICT" && (
            <p role="alert" className="text-crm-tone-orange">
              Hai nguồn có kết quả khác nhau. Điều phối viên cần xác nhận.
            </p>
          )}
        </div>
      )}
      {!classified && <p className="mb-2 text-crm-text-2">AI chưa đủ thông tin để phân loại.</p>}
      <dl>
        {classified && result.category && (
          <FieldRow label="Loại sự cố">{CATEGORY_LABELS[result.category]}</FieldRow>
        )}
        {result.impact && <FieldRow label="Ảnh hưởng">{IMPACT_LABELS[result.impact]}</FieldRow>}
        {result.urgency && <FieldRow label="Độ gấp">{URGENCY_LABELS[result.urgency]}</FieldRow>}
        {result.priority && (
          <FieldRow label="Ưu tiên">
            <PriorityTag priority={result.priority} />
          </FieldRow>
        )}
      </dl>
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
      {modelEnabled &&
        (result.engine === "rules" || result.cooperation?.modelStatus === "UNAVAILABLE") &&
        retry}
    </section>
  );
};
