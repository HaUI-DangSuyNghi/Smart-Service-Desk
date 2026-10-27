import { useEffect, useRef, useState, type FormEvent } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useAuth } from "../../../platform/human/auth";
import { ApiError } from "../../../platform/shared/api";
import { Button } from "../../../platform/shared/ui/button";
import { Input } from "../../../platform/shared/ui/input";
import {
  appendClarification,
  assessCurrentContext,
  evaluateEnrichment,
  getContextRevision,
  getEnrichmentState,
  type ClarificationInput,
} from "../services/enrichmentService";
import { ENRICHMENT_FIELDS, ENRICHMENT_STATUS } from "./enrichmentFields";

interface Props {
  workspace: string;
  ticketId: string;
  onRevisionChange: (revision: number) => void;
}

/** Câu trả lời là dữ liệu người gửi, không phải lệnh tạm dừng hoặc phê duyệt xử lý. */
export const EnrichmentPanel = ({ workspace, ticketId, onRevisionChange }: Props) => {
  const { user } = useAuth();
  const cache = useQueryClient();
  const actor = user?.profile.sub ?? "";
  const revisionKey = ["servicedesk", "context-revision", workspace, ticketId, actor];
  const stateKey = ["servicedesk", "enrichment-state", workspace, ticketId, actor];
  const revision = useQuery({
    queryKey: revisionKey,
    queryFn: ({ signal }) => getContextRevision(workspace, ticketId, signal),
    retry: false,
  });
  const lifecycle = useQuery({
    queryKey: stateKey,
    queryFn: ({ signal }) => getEnrichmentState(workspace, ticketId, signal),
    enabled: revision.data !== undefined && !revision.error,
    retry: false,
    refetchInterval: (query) =>
      query.state.data &&
      !query.state.data.manualReviewRequired &&
      ["NEW", "REASSESS_QUEUED", "EVALUATING"].includes(query.state.data.status)
        ? 2000
        : false,
  });
  const [drafts, setDrafts] = useState<Record<string, string>>({});
  const [corrections, setCorrections] = useState<Record<string, boolean>>({});
  const [manualSlot, setManualSlot] = useState("device.identifier");
  const [message, setMessage] = useState("");
  const [formError, setFormError] = useState("");
  const errorRef = useRef<HTMLParagraphElement>(null);
  const pendingRequest = useRef<{ fingerprint: string; input: ClarificationInput } | null>(null);
  const evaluated = useRef(new Set<string>());
  const save = useMutation({
    mutationFn: (input: ClarificationInput) => appendClarification(workspace, ticketId, input),
  });
  const check = useMutation({ mutationFn: () => assessCurrentContext(workspace, ticketId) });
  const { mutate: runEvaluation, isPending: evaluating } = useMutation({
    mutationFn: () => evaluateEnrichment(workspace, ticketId),
    onSuccess: (state) => cache.setQueryData(stateKey, state),
  });

  useEffect(() => {
    if (revision.data !== undefined) onRevisionChange(revision.data);
  }, [revision.data, onRevisionChange]);

  useEffect(() => {
    if (formError) errorRef.current?.focus();
  }, [formError]);

  const current = lifecycle.error ? null : lifecycle.data;
  const pendingRevision =
    current && !current.manualReviewRequired && ["NEW", "REASSESS_QUEUED"].includes(current.status)
      ? current.contextRevision
      : null;
  useEffect(() => {
    if (pendingRevision === null) return;
    const key = `${workspace}/${ticketId}/${pendingRevision}`;
    if (evaluated.current.has(key)) return;
    // Chỉ trì hoãn command UI; deadline và nguồn sự thật vẫn ở DB.
    const timer = window.setTimeout(() => {
      evaluated.current.add(key);
      runEvaluation();
    }, 900);
    return () => window.clearTimeout(timer);
  }, [pendingRevision, workspace, ticketId, runEvaluation]);

  const fresh = current?.contextRevision === revision.data;
  const questions = fresh ? (current?.questions ?? []) : [];
  const fields = new Map(
    questions.map((question) => [
      question.slot,
      {
        slot: question.slot,
        text: question.text,
        correctionRequired: question.correctionRequired,
      },
    ]),
  );
  if (!questions.length) {
    fields.set(manualSlot, {
      slot: manualSlot,
      text: ENRICHMENT_FIELDS[manualSlot].label,
      correctionRequired: false,
    });
  }
  // Xung đột revision không làm biến mất nội dung người dùng chưa gửi.
  Object.keys(drafts)
    .filter((slot) => drafts[slot].trim())
    .forEach((slot) => {
      if (!fields.has(slot))
        fields.set(slot, {
          slot,
          text: ENRICHMENT_FIELDS[slot]?.label ?? "Thông tin bổ sung",
          correctionRequired: false,
        });
    });

  const submit = async (event: FormEvent) => {
    event.preventDefault();
    setFormError("");
    setMessage("");
    const answers = [...fields.values()]
      .filter(({ slot }) => drafts[slot]?.trim())
      .map(({ slot }) => ({
        slot,
        value: drafts[slot].trim(),
        correction: corrections[slot] ?? false,
      }));
    if (
      !answers.length ||
      answers.length > 8 ||
      answers.some((answer) => answer.value.length > 256)
    ) {
      setFormError("Nhập từ một đến tám thông tin, mỗi nội dung tối đa 256 ký tự.");
      return;
    }
    if (
      [...fields.values()].some(
        (field) =>
          field.correctionRequired && drafts[field.slot]?.trim() && !corrections[field.slot],
      )
    ) {
      setFormError("Hãy xác nhận đính chính cho thông tin đang mâu thuẫn trước khi gửi.");
      return;
    }
    if (revision.data === undefined || revision.error) return;
    // Mất response không được đổi answer ID chỉ vì GET revision đã thấy lần lưu trước.
    const fingerprint = JSON.stringify(answers);
    if (pendingRequest.current?.fingerprint !== fingerprint) {
      pendingRequest.current = {
        fingerprint,
        input: {
          contractVersion: "ticket-clarification@0.1.0",
          clientAnswerId: crypto.randomUUID(),
          expectedContextRevision: revision.data,
          answers,
        },
      };
    }
    try {
      await save.mutateAsync(pendingRequest.current.input);
      pendingRequest.current = null;
      setDrafts({});
      setCorrections({});
      await revision.refetch();
      await lifecycle.refetch();
      setMessage(
        "Đã lưu bổ sung vào ticket này. Thông tin mới sẽ được kiểm tra lại trước khi sử dụng.",
      );
    } catch (error) {
      if (error instanceof ApiError && error.status === 409) {
        pendingRequest.current = null;
        await revision.refetch();
        await lifecycle.refetch();
        setFormError(
          "Thông tin đã được cập nhật ở nơi khác. Nội dung của bạn vẫn được giữ; kiểm tra lại rồi gửi lại.",
        );
      } else {
        setFormError(
          "Chưa xác nhận được việc lưu. Nội dung vẫn được giữ; bạn có thể gửi lại an toàn.",
        );
      }
    }
  };

  if (revision.isPending)
    return (
      <p role="status" className="p-4 text-crm-text-2">
        Đang tải thông tin bổ sung…
      </p>
    );
  if (revision.error)
    return (
      <div className="p-4 text-crm-text-2">
        <p role="alert">Chưa tải được thông tin, hoặc bạn không có quyền xem ticket.</p>
        <Button variant="outline" className="mt-3" onClick={() => void revision.refetch()}>
          Thử tải lại
        </Button>
      </div>
    );

  const assessment =
    !check.error && check.data?.contextRevision === revision.data ? check.data : null;
  const manual = current?.manualReviewRequired;
  return (
    <section aria-labelledby="enrichment-title" className="space-y-4 border-b border-crm-line p-4">
      <div className="space-y-2">
        <h3 id="enrichment-title" className="text-crm-lg font-semibold">
          Bổ sung thông tin
        </h3>
        {current && (
          <p role="status" className="font-medium text-crm-text">
            {ENRICHMENT_STATUS[current.status]}
          </p>
        )}
        <p className="text-crm-sm text-crm-text-2">
          Yêu cầu bổ sung không tạm dừng quy trình hoặc thay đổi hạn xử lý của ticket.
        </p>
        {manual && (
          <p className="text-crm-sm text-crm-text-2">
            Điều phối viên cần xem xét. Bạn vẫn có thể bổ sung; thông tin mới không tự mở lại hoặc
            thay đổi công việc đang xử lý.
          </p>
        )}
        {fresh && current?.deadlineAt && !manual && (
          <p className="text-crm-sm text-crm-text-2">
            Mốc chuyển kiểm tra thủ công:{" "}
            {new Intl.DateTimeFormat("vi-VN", { dateStyle: "short", timeStyle: "short" }).format(
              new Date(current.deadlineAt),
            )}
          </p>
        )}
        {lifecycle.error && (
          <p role="alert" className="text-crm-sm text-crm-text-2">
            Chưa tải được trạng thái kiểm tra. Bạn vẫn có thể lưu thông tin bổ sung.
          </p>
        )}
      </div>
      <form onSubmit={(event) => void submit(event)} className="space-y-4">
        {formError && (
          <p ref={errorRef} tabIndex={-1} role="alert" className="text-crm-sm text-crm-danger">
            {formError}
          </p>
        )}
        <fieldset disabled={save.isPending} className="space-y-4" aria-busy={save.isPending}>
          <legend className="sr-only">Nội dung bổ sung cho ticket hiện tại</legend>
          {!questions.length && (
            <label className="block space-y-2 text-crm-sm">
              <span>Loại thông tin bổ sung</span>
              <select
                value={manualSlot}
                onChange={(event) => setManualSlot(event.target.value)}
                className="min-h-11 w-full rounded-lg border border-input bg-background px-3"
              >
                {Object.entries(ENRICHMENT_FIELDS).map(([slot, value]) => (
                  <option key={slot} value={slot}>
                    {value.label}
                  </option>
                ))}
              </select>
            </label>
          )}
          {[...fields.values()].map((field) => {
            const choices = ENRICHMENT_FIELDS[field.slot]?.choices;
            const id = `answer-${field.slot}`;
            return (
              <div key={field.slot} className="space-y-2">
                <label htmlFor={id} className="block text-crm-sm font-medium">
                  {field.text}
                </label>
                {choices ? (
                  <select
                    id={id}
                    value={drafts[field.slot] ?? ""}
                    onChange={(event) =>
                      setDrafts((previous) => ({ ...previous, [field.slot]: event.target.value }))
                    }
                    className="min-h-11 w-full rounded-lg border border-input bg-background px-3"
                  >
                    <option value="">Chọn thông tin</option>
                    {choices.map(([value, label]) => (
                      <option key={value} value={value}>
                        {label}
                      </option>
                    ))}
                  </select>
                ) : (
                  <Input
                    id={id}
                    value={drafts[field.slot] ?? ""}
                    maxLength={256}
                    className="min-h-11"
                    onChange={(event) =>
                      setDrafts((previous) => ({ ...previous, [field.slot]: event.target.value }))
                    }
                  />
                )}
                <label className="flex min-h-8 items-center gap-2 text-crm-sm text-crm-text-2">
                  <input
                    type="checkbox"
                    aria-label={`Đính chính ${ENRICHMENT_FIELDS[field.slot]?.label ?? field.text}`}
                    checked={corrections[field.slot] ?? false}
                    onChange={(event) =>
                      setCorrections((previous) => ({
                        ...previous,
                        [field.slot]: event.target.checked,
                      }))
                    }
                  />
                  Thông tin này đính chính lời khai trước
                </label>
              </div>
            );
          })}
          <p className="text-crm-sm text-crm-text-3">
            Không gửi mật khẩu, mã OTP hoặc thông tin bí mật.
          </p>
          <Button type="submit" className="min-h-11" disabled={save.isPending}>
            {save.isPending ? "Đang lưu…" : "Gửi bổ sung"}
          </Button>
        </fieldset>
      </form>
      {message && (
        <p role="status" className="text-crm-sm text-crm-text-2">
          {message}
        </p>
      )}
      <div className="space-y-3 border-t border-crm-line pt-4">
        <Button
          variant="outline"
          disabled={check.isPending || evaluating}
          onClick={() => check.mutate()}
        >
          {check.isPending ? "Đang kiểm tra…" : "Đánh giá từ thông tin hiện tại"}
        </Button>
        {check.error && (
          <p role="alert" className="text-crm-sm text-crm-text-2">
            Chưa thể đánh giá lại. Ticket vẫn được xử lý; bạn có thể thử lại hoặc nhờ điều phối viên
            xem xét.
          </p>
        )}
        {assessment?.ai07 && (
          <div className="space-y-2 text-crm-sm">
            <p className="font-medium">Gợi ý từ thông tin hiện tại — cần người kiểm tra</p>
            <dl className="grid grid-cols-2 gap-2">
              <dt>Loại sự cố</dt>
              <dd>
                {(
                  {
                    ACCOUNT: "Tài khoản",
                    HARDWARE: "Phần cứng",
                    NETWORK: "Mạng",
                    SOFTWARE: "Phần mềm",
                    OTHER: "Khác",
                  } as Record<string, string>
                )[assessment.ai07.category ?? ""] ?? "Chưa xác định"}
              </dd>
              <dt>Mức ưu tiên gợi ý</dt>
              <dd>
                {(
                  {
                    LOW: "Thấp",
                    MEDIUM: "Trung bình",
                    HIGH: "Cao",
                    CRITICAL: "Khẩn cấp",
                  } as Record<string, string>
                )[assessment.ai07.priority ?? ""] ?? "Chưa đủ dữ kiện"}
              </dd>
            </dl>
            <p className="text-crm-text-2">
              Dữ kiện do người gửi cung cấp, chưa phải thông tin đã được xác minh.
            </p>
          </div>
        )}
      </div>
    </section>
  );
};
