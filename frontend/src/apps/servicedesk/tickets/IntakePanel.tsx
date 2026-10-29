import { useRef, useState } from "react";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { useActor } from "../../../platform/human/workspace";
import { Button } from "../../../platform/shared/ui/button";
import { Input } from "../../../platform/shared/ui/input";
import { SelectField } from "../../../platform/shared/ui/SelectField";
import { PRIORITY_LABELS } from "../../../platform/process";
import { ErrorState } from "../../../platform/shared/ui/ErrorState";
import {
  evaluateIntake,
  releaseIntake,
  reviewIntake,
  type Intake,
} from "../services/intakeService";
import { CanonicalPanel } from "./CanonicalPanel";
import { intakeSuggestion } from "./reviewForm";
import { CATEGORY_LABELS } from "./ticketLabels";
import { CATEGORIES, PRIORITIES } from "./ticketRules";

const CATEGORY_OPTIONS = CATEGORIES.map((value) => ({ value, label: CATEGORY_LABELS[value] }));
const PRIORITY_OPTIONS = PRIORITIES.map((value) => ({ value, label: PRIORITY_LABELS[value] }));
// Dropdown trong form: rộng hết ô
const FORM_SELECT = "h-11 w-full max-w-none sm:h-9";

const STATUS: Record<Intake["status"], string> = {
  NEW: "Đang chờ kiểm tra thông tin",
  NEEDS_ENRICHMENT: "Cần bổ sung thông tin",
  DUPLICATE_HOLD: "Cần kiểm tra ticket trùng",
  FINAL_ASSESSMENT_QUEUED: "Đang chờ đánh giá cuối",
  ASSESSING: "Đang đánh giá",
  REVIEW_RELEASED: "Đã có đề xuất để điều phối viên duyệt",
  MANUAL_REQUIRED: "Cần điều phối viên xem xét",
  REVIEWED: "Điều phối viên đã duyệt",
  CANONICAL_FOLLOWER: "Đang theo dõi sự cố đã được liên kết",
};

export const IntakePanel = ({
  workspace,
  ticketId,
  state,
  independent,
}: {
  workspace: string;
  ticketId: string;
  state: Intake;
  independent: boolean;
}) => {
  const { roles } = useActor();
  const cache = useQueryClient();
  const [reason, setReason] = useState("");
  // null: chưa tự chọn → theo đề xuất AI (đề xuất có thể tới sau khi mở trang)
  const [pickedCategory, setCategory] = useState<string | null>(null);
  const [pickedPriority, setPriority] = useState<string | null>(null);
  const suggestion = intakeSuggestion(state.finalAssessment?.ai07);
  const category = pickedCategory ?? suggestion?.category ?? "OTHER";
  const priority = pickedPriority ?? suggestion?.priority ?? "MEDIUM";
  const release = useRef<{ fingerprint: string; operation: string } | null>(null);
  const refresh = async () => {
    await cache.invalidateQueries({ queryKey: ["servicedesk"] });
    await cache.invalidateQueries({ queryKey: ["process"] });
  };
  const action = useMutation({
    mutationFn: async (kind: "check" | "release" | "review") => {
      if (kind === "check") return evaluateIntake(workspace, ticketId);
      if (kind === "review")
        return reviewIntake(workspace, ticketId, state, category, priority, reason.trim());
      const fingerprint = JSON.stringify([state.contextRevision, state.version, reason.trim()]);
      if (release.current?.fingerprint !== fingerprint)
        release.current = { fingerprint, operation: crypto.randomUUID() };
      return releaseIntake(workspace, ticketId, state, reason.trim(), release.current.operation);
    },
    onSuccess: refresh,
    onError: refresh,
  });
  const coordinator = independent && roles.includes("coordinator");
  const finished = ["REVIEWED", "CANONICAL_FOLLOWER"].includes(state.status);
  const released =
    ["REVIEW_RELEASED", "MANUAL_REQUIRED"].includes(state.status) && state.reviewTaskMayExist;
  const proposal = state.finalAssessment?.ai07;
  return (
    <>
      <section
        aria-label="Tiếp nhận và đánh giá ticket"
        className="space-y-4 border-b border-crm-line p-4"
      >
        <h2 className="font-semibold">Tiếp nhận ticket</h2>
        <p role="status" aria-live="polite">
          {STATUS[state.status]}
        </p>
        <p className="text-crm-sm text-crm-text-2">
          Thông tin lần {state.contextRevision + 1} · Hạn xem xét:{" "}
          {new Date(state.deadlineAt).toLocaleString("vi-VN")}
        </p>
        {state.lateNotice && (
          <p role="alert">Có thông tin bổ sung sau quyết định. Điều phối viên cần kiểm tra lại.</p>
        )}
        {state.readiness?.questions?.map((question) => (
          <p key={question.slot}>{question.text}</p>
        ))}
        {proposal && (
          <div className="space-y-2 rounded-lg border border-crm-line p-3">
            <p className="font-medium">Đề xuất đã lưu, cần người kiểm tra</p>
            <p>
              Loại: {suggestion?.category ? CATEGORY_LABELS[suggestion.category] : "Chưa xác định"}{" "}
              · Ưu tiên:{" "}
              {suggestion?.priority ? PRIORITY_LABELS[suggestion.priority] : "Cần xem xét"}
            </p>
          </div>
        )}
        {!finished && (
          <Button
            variant="outline"
            className="min-h-11"
            disabled={action.isPending}
            onClick={() => action.mutate("check")}
          >
            Kiểm tra thông tin bổ sung
          </Button>
        )}
        {coordinator && !finished && (
          <fieldset disabled={action.isPending} className="space-y-3">
            <legend className="mb-3 font-medium">Quyết định của điều phối viên</legend>
            {released && (
              <>
                <div className="space-y-1">
                  <p>Loại sự cố</p>
                  <SelectField
                    aria-label="Loại sự cố"
                    options={CATEGORY_OPTIONS}
                    value={category}
                    onValueChange={setCategory}
                    className={FORM_SELECT}
                  />
                </div>
                <div className="space-y-1">
                  <p>Mức ưu tiên</p>
                  <SelectField
                    aria-label="Mức ưu tiên"
                    options={PRIORITY_OPTIONS}
                    value={priority}
                    onValueChange={setPriority}
                    className={FORM_SELECT}
                  />
                </div>
              </>
            )}
            <label className="block space-y-1">
              <span>Lý do quyết định</span>
              <Input
                className="min-h-11"
                value={reason}
                maxLength={1000}
                onChange={(event) => setReason(event.target.value)}
              />
            </label>
            <Button
              className="min-h-11"
              disabled={!reason.trim()}
              onClick={() => action.mutate(released ? "review" : "release")}
            >
              {released ? "Duyệt và chuyển xử lý" : "Chuyển sang duyệt thủ công"}
            </Button>
          </fieldset>
        )}
        {action.isPending && <p role="status">Đang lưu quyết định…</p>}
        {action.error && (
          <ErrorState
            error={action.error}
            fallback="Chưa lưu được. Kiểm tra trạng thái mới nhất trước khi thử lại."
          />
        )}
      </section>
      {coordinator && <CanonicalPanel workspace={workspace} ticketId={ticketId} state={state} />}
    </>
  );
};
