import { useState } from "react";
import { IconInbox } from "@tabler/icons-react";
import { useActor } from "../../../platform/human/workspace";
import { Button } from "../../../platform/shared/ui/button";
import { Tag } from "../../../platform/shared/ui/Tag";
import type { Intake } from "../services/intakeService";
import { CanonicalPanel } from "./CanonicalPanel";
import { ReleaseIntakeDialog } from "./ReleaseIntakeDialog";

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

// Đã sang bước duyệt (nút "Duyệt và giao việc") hoặc đã xong: không tiếp nhận nữa
const PAST_INTAKE: ReadonlySet<Intake["status"]> = new Set([
  "REVIEW_RELEASED",
  "REVIEWED",
  "CANONICAL_FOLLOWER",
]);
/**
 * Trạng thái tiếp nhận. Điều phối viên (không phải người gửi) có nút Tiếp nhận để chuyển sang
 * Chờ duyệt; sau đó duyệt bằng nút "Duyệt và giao việc" (form điền sẵn theo AI).
 */
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
  const coordinator = independent && roles.includes("coordinator");
  const [releasing, setReleasing] = useState(false);
  return (
    <>
      <section
        aria-label="Tiếp nhận ticket"
        className="flex flex-col gap-2 border-b border-crm-line px-4 py-3"
      >
        <h2 className="font-medium">Tiếp nhận</h2>
        {/* Chỉ trạng thái; không hiện số lần, hạn hay câu hỏi của AI (rule 05) */}
        <div role="status" aria-live="polite" className="flex flex-wrap items-center gap-1.5">
          <Tag tone={state.status === "MANUAL_REQUIRED" ? "orange" : "gray"}>
            {STATUS[state.status]}
          </Tag>
          {state.lateNotice && <Tag tone="orange">Có thông tin mới</Tag>}
        </div>
        {coordinator && !PAST_INTAKE.has(state.status) && (
          <div>
            <Button size="sm" className="h-9 md:h-7" onClick={() => setReleasing(true)}>
              <IconInbox size={14} stroke={2} />
              Tiếp nhận
            </Button>
          </div>
        )}
      </section>
      <ReleaseIntakeDialog
        target={releasing ? { workspace, id: ticketId } : null}
        onClose={() => setReleasing(false)}
      />
      {coordinator && <CanonicalPanel workspace={workspace} ticketId={ticketId} state={state} />}
    </>
  );
};
