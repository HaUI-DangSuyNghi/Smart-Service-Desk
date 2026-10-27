import { useRef, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Link } from "react-router";
import { Button } from "../../../platform/shared/ui/button";
import { Input } from "../../../platform/shared/ui/input";
import { ErrorState } from "../../../platform/shared/ui/ErrorState";
import {
  canonicalCommand,
  preflightIntake,
  readCanonical,
  type Intake,
} from "../services/intakeService";

/** This panel proposes no automatic merge. The server independently rechecks every assertion. */
export const CanonicalPanel = ({
  workspace,
  ticketId,
  state,
}: {
  workspace: string;
  ticketId: string;
  state: Intake;
}) => {
  const cache = useQueryClient();
  const following = state.status === "CANONICAL_FOLLOWER";
  const current = useQuery({
    queryKey: ["servicedesk", "canonical", workspace, ticketId],
    queryFn: ({ signal }) => readCanonical(workspace, ticketId, signal),
    enabled: following,
    retry: false,
  });
  const probe = useMutation({ mutationFn: () => preflightIntake(workspace, ticketId) });
  const [root, setRoot] = useState("");
  const [category, setCategory] = useState("HARDWARE");
  const [reason, setReason] = useState("");
  const [verified, setVerified] = useState(false);
  const operation = useRef<{ fingerprint: string; id: string } | null>(null);
  const save = useMutation({
    mutationFn: () => {
      const candidate = probe.data?.candidates.find((value) => value.candidateRef === root);
      const command = following
        ? {
            expectedLinkVersion: current.data?.version,
            expectedContextRevision: state.contextRevision,
            reason: reason.trim(),
          }
        : {
            rootTicketId: root,
            expectedContextRevision: state.contextRevision,
            expectedRootRevision: candidate?.contextRevision,
            expectedIntakeVersion: state.version,
            candidateSetDigest: probe.data?.candidateSetDigest,
            verifiedCategory: category,
            reason: reason.trim(),
          };
      const fingerprint = JSON.stringify(command);
      if (operation.current?.fingerprint !== fingerprint)
        operation.current = { fingerprint, id: crypto.randomUUID() };
      return canonicalCommand(workspace, ticketId, following, {
        ...command,
        operationId: operation.current.id,
      });
    },
    onSuccess: async () => {
      await cache.invalidateQueries({ queryKey: ["servicedesk"] });
      await cache.invalidateQueries({ queryKey: ["process"] });
    },
  });
  if (state.reviewTaskMayExist || state.status === "REVIEWED") return null;
  return (
    <details className="border-b border-crm-line p-4">
      <summary className="cursor-pointer py-2 font-medium">
        {following ? "Kiểm tra liên kết sự cố" : "Kiểm tra ticket cùng sự cố"}
      </summary>
      <div className="space-y-4 pt-4">
        <p className="text-crm-sm text-crm-text-2">
          Chỉ liên kết sau khi xác minh cùng phòng, cùng thiết bị vật lý, triệu chứng phù hợp và
          loại sự cố đã được người duyệt. Chính sách phải cho phép thao tác.
        </p>
        {!following && (
          <Button
            variant="outline"
            className="min-h-11"
            disabled={probe.isPending || save.isPending}
            onClick={() => {
              setRoot("");
              setVerified(false);
              probe.mutate();
            }}
          >
            Tìm ticket cần kiểm tra
          </Button>
        )}
        {probe.data?.candidateSetIncomplete && (
          <p role="alert">Danh sách chưa đầy đủ. Cần xử lý ticket độc lập.</p>
        )}
        {probe.data?.incidentSignal && (
          <p role="status">
            Có dấu hiệu sự cố theo khu vực; cần điều phối viên kiểm tra phạm vi ảnh hưởng.
          </p>
        )}
        {!following && probe.data && (
          <>
            <label className="block space-y-1">
              <span>Ticket gốc đã xác minh</span>
              <select
                aria-label="Ticket gốc đã xác minh"
                className="min-h-11 w-full rounded-lg border border-input bg-background px-3"
                value={root}
                onChange={(event) => {
                  setRoot(event.target.value);
                  setVerified(false);
                }}
              >
                <option value="">Chọn ticket để đối chiếu</option>
                {probe.data.candidates
                  .filter((value) => value.strongUnverified)
                  .map((value) => (
                    <option key={value.candidateRef} value={value.candidateRef}>
                      {value.candidateRef}
                    </option>
                  ))}
              </select>
            </label>
            {root && (
              <Link
                className="underline"
                to={`/servicedesk/workspaces/${encodeURIComponent(workspace)}/tickets/${root}`}
                target="_blank"
                rel="noreferrer"
              >
                Mở ticket gốc để đối chiếu
              </Link>
            )}
            <label className="block space-y-1">
              <span>Loại sự cố đã được người duyệt tại ticket gốc</span>
              <select
                aria-label="Loại đã xác minh"
                className="min-h-11 w-full rounded-lg border border-input bg-background px-3"
                value={category}
                onChange={(event) => {
                  setCategory(event.target.value);
                  setVerified(false);
                }}
              >
                {Object.entries({
                  ACCOUNT: "Tài khoản",
                  HARDWARE: "Phần cứng",
                  NETWORK: "Mạng",
                  SOFTWARE: "Phần mềm",
                  OTHER: "Khác",
                }).map(([value, label]) => (
                  <option key={value} value={value}>
                    {label}
                  </option>
                ))}
              </select>
            </label>
            <label className="flex min-h-11 items-center gap-3">
              <input
                type="checkbox"
                checked={verified}
                onChange={(event) => setVerified(event.target.checked)}
              />
              Tôi đã đối chiếu bằng chứng vật lý và quyết định duyệt tại ticket gốc.
            </label>
          </>
        )}
        {following && current.data && (
          <p>
            Tiến độ liên kết:{" "}
            {current.data.publicProgress === "ROOT_CONFIRMED"
              ? "Ticket gốc đã được xác nhận"
              : "Đang theo dõi ticket gốc"}
          </p>
        )}
        {(following || probe.data) && (
          <>
            <label className="block space-y-1">
              <span>Lý do {following ? "tách" : "liên kết"}</span>
              <Input
                className="min-h-11"
                value={reason}
                maxLength={1000}
                onChange={(event) => setReason(event.target.value)}
              />
            </label>
            <Button
              variant="outline"
              className="min-h-11"
              disabled={
                save.isPending ||
                !reason.trim() ||
                (following
                  ? !current.data
                  : !root || !verified || probe.data?.candidateSetIncomplete)
              }
              onClick={() => save.mutate()}
            >
              {following ? "Tách và tiếp nhận độc lập" : "Xác nhận liên kết cùng sự cố"}
            </Button>
          </>
        )}
        {(probe.isPending || save.isPending) && <p role="status">Đang kiểm tra…</p>}
        {(probe.error || save.error || current.error) && (
          <ErrorState error={probe.error ?? save.error ?? current.error} />
        )}
      </div>
    </details>
  );
};
