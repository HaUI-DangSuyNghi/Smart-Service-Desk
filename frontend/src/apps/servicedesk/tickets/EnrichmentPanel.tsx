import { useEffect, useRef, useState, type FormEvent } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useAuth } from "../../../platform/human/auth";
import { ApiError } from "../../../platform/shared/api";
import { Button } from "../../../platform/shared/ui/button";
import { Input } from "../../../platform/shared/ui/input";
import { SelectField } from "../../../platform/shared/ui/SelectField";
import {
  appendClarification,
  evaluateEnrichment,
  getContextRevision,
  getEnrichmentState,
  type ClarificationInput,
} from "../services/enrichmentService";
import { ENRICHMENT_FIELDS, ENRICHMENT_STATUS } from "./enrichmentFields";

interface Props {
  workspace: string;
  ticketId: string;
  onRevisionChange?: (revision: number) => void;
  managedIntake?: boolean;
  /** Đặt trong dialog đã có tiêu đề: bỏ tiêu đề và khung riêng của khối. */
  embedded?: boolean;
}

const SLOT_OPTIONS = Object.entries(ENRICHMENT_FIELDS).map(([value, field]) => ({
  value,
  label: field.label,
}));
// Dropdown trong form: rộng hết ô, cao như input
const FORM_SELECT = "h-11 w-full max-w-none sm:h-9";
// Gợi ý cách nhập; backend nhận chữ tự do và tự chuẩn hóa
const PLACEHOLDERS: Record<string, string> = {
  "device.identifier": "VD: máy 15, PC-A201-07",
  "location.room": "VD: 1304, A201",
  "device.type": "VD: máy chiếu, máy in",
  symptomCodes: "VD: không lên nguồn, mất mạng",
  affectedScope: "VD: cả phòng, vài máy",
  affectedCount: "VD: 30",
  workBlocked: "Có / Không",
  workaroundAvailable: "Có / Không",
  timeWindow: "VD: trước 14h hôm nay",
};

/** Câu trả lời là dữ liệu người gửi, không phải lệnh tạm dừng hoặc phê duyệt xử lý. */
export const EnrichmentPanel = ({
  workspace,
  ticketId,
  onRevisionChange,
  managedIntake = false,
  embedded = false,
}: Props) => {
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
    enabled: !managedIntake && revision.data !== undefined && !revision.error,
    retry: false,
    refetchInterval: (query) =>
      query.state.data &&
      !query.state.data.manualReviewRequired &&
      ["NEW", "REASSESS_QUEUED", "EVALUATING"].includes(query.state.data.status)
        ? 2000
        : false,
  });
  const [drafts, setDrafts] = useState<Record<string, string>>({});
  const [manualSlot, setManualSlot] = useState("device.identifier");
  const [message, setMessage] = useState("");
  const [formError, setFormError] = useState("");
  const errorRef = useRef<HTMLParagraphElement>(null);
  const pendingRequest = useRef<{ fingerprint: string; input: ClarificationInput } | null>(null);
  const evaluated = useRef(new Set<string>());
  const save = useMutation({
    mutationFn: (input: ClarificationInput) => appendClarification(workspace, ticketId, input),
  });
  const { mutate: runEvaluation } = useMutation({
    mutationFn: () => evaluateEnrichment(workspace, ticketId),
    onSuccess: (state) => cache.setQueryData(stateKey, state),
  });

  useEffect(() => {
    if (revision.data !== undefined) onRevisionChange?.(revision.data);
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
    if (managedIntake || pendingRevision === null) return;
    const key = `${workspace}/${ticketId}/${pendingRevision}`;
    if (evaluated.current.has(key)) return;
    // Chỉ trì hoãn command UI; deadline và nguồn sự thật vẫn ở DB.
    const timer = window.setTimeout(() => {
      evaluated.current.add(key);
      runEvaluation();
    }, 900);
    return () => window.clearTimeout(timer);
  }, [pendingRevision, workspace, ticketId, runEvaluation, managedIntake]);

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
      // AI hỏi lại do mâu thuẫn thì câu trả lời tự tính là đính chính
      .map(({ slot, correctionRequired }) => ({
        slot,
        value: drafts[slot].trim(),
        correction: correctionRequired,
      }));
    if (
      !answers.length ||
      answers.length > 8 ||
      answers.some((answer) => answer.value.length > 256)
    ) {
      setFormError("Nhập từ một đến tám thông tin, mỗi nội dung tối đa 256 ký tự.");
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
      await revision.refetch();
      if (!managedIntake) await lifecycle.refetch();
      await cache.invalidateQueries({ queryKey: ["servicedesk", "intake", workspace, ticketId] });
      setMessage(
        "Đã lưu bổ sung vào ticket này. Thông tin mới sẽ được kiểm tra lại trước khi sử dụng.",
      );
    } catch (error) {
      if (error instanceof ApiError && error.status === 409) {
        pendingRequest.current = null;
        await revision.refetch();
        if (!managedIntake) await lifecycle.refetch();
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

  const manual = current?.manualReviewRequired;
  return (
    <section
      aria-labelledby={embedded ? undefined : "enrichment-title"}
      className={embedded ? "space-y-4" : "space-y-4 border-b border-crm-line p-4"}
    >
      <div className="space-y-2">
        {!embedded && (
          <h3 id="enrichment-title" className="text-crm-lg font-semibold">
            Bổ sung thông tin
          </h3>
        )}
        {current && (
          <p role="status" className="font-medium text-crm-text">
            {ENRICHMENT_STATUS[current.status]}
          </p>
        )}
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
            <div className="space-y-2 text-crm-sm">
              <p className="font-medium">Loại thông tin bổ sung</p>
              <SelectField
                aria-label="Loại thông tin bổ sung"
                options={SLOT_OPTIONS}
                value={manualSlot}
                onValueChange={(slot) => {
                  // Đổi loại thì giữ chữ đang nhập, không sinh thêm ô
                  setDrafts((previous) => ({ [slot]: previous[manualSlot] ?? "" }));
                  setManualSlot(slot);
                }}
                className={FORM_SELECT}
              />
            </div>
          )}
          {[...fields.values()].map((field) => {
            const id = `answer-${field.slot}`;
            return (
              <div key={field.slot} className="space-y-2">
                {/* Tự chọn loại: dropdown đã là nhãn, chỉ còn ô nhập */}
                <label
                  htmlFor={id}
                  className={questions.length ? "block text-crm-sm font-medium" : "sr-only"}
                >
                  {field.text}
                </label>
                <Input
                  id={id}
                  value={drafts[field.slot] ?? ""}
                  maxLength={256}
                  placeholder={PLACEHOLDERS[field.slot] ?? "Nhập thông tin"}
                  className="h-11 sm:h-9"
                  onChange={(event) =>
                    setDrafts((previous) => ({ ...previous, [field.slot]: event.target.value }))
                  }
                />
              </div>
            );
          })}
          <div className="flex justify-end">
            <Button type="submit" className="h-11 sm:h-9" disabled={save.isPending}>
              {save.isPending ? "Đang lưu…" : "Gửi bổ sung"}
            </Button>
          </div>
        </fieldset>
      </form>
      {message && (
        <p role="status" className="text-crm-sm text-crm-text-2">
          {message}
        </p>
      )}
    </section>
  );
};
