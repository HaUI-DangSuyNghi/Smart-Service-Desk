import { useRef, useState } from "react";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { Button } from "../../../platform/shared/ui/button";
import {
  Dialog,
  DialogContent,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "../../../platform/shared/ui/dialog";
import { ErrorState } from "../../../platform/shared/ui/ErrorState";
import { FormField } from "../../../platform/shared/ui/FormField";
import { FieldsSkeleton } from "../../../platform/shared/ui/Skeleton";
import { Textarea } from "../../../platform/shared/ui/textarea";
import { toast, toastError } from "../../../platform/shared/ui/toast";
import { releaseIntake, type Intake } from "../services/intakeService";
import { useIntake } from "./useIntake";

const MAX_REASON = 1000;

export interface ReleaseTarget {
  workspace: string;
  id: string;
}

const ReleaseForm = ({
  target,
  state,
  onDone,
}: {
  target: ReleaseTarget;
  state: Intake;
  onDone: () => void;
}) => {
  const cache = useQueryClient();
  const [reason, setReason] = useState("Đã kiểm tra thông tin, chuyển sang duyệt");
  // Gửi lại sau lỗi mạng dùng cùng mã thao tác để backend không tiếp nhận hai lần
  const operation = useRef<{ fingerprint: string; id: string } | null>(null);
  const release = useMutation({
    mutationFn: () => {
      const fingerprint = JSON.stringify([state.contextRevision, state.version, reason.trim()]);
      if (operation.current?.fingerprint !== fingerprint)
        operation.current = { fingerprint, id: crypto.randomUUID() };
      return releaseIntake(target.workspace, target.id, state, reason.trim(), operation.current.id);
    },
    onSuccess: () => {
      toast.success("Đã tiếp nhận");
      onDone();
    },
    onError: (error) => toastError("Chưa tiếp nhận được.", error),
    onSettled: async () => {
      await cache.invalidateQueries({ queryKey: ["servicedesk"] });
      await cache.invalidateQueries({ queryKey: ["process"] });
    },
  });

  return (
    <>
      <form
        id="release-intake"
        onSubmit={(event) => {
          event.preventDefault();
          if (reason.trim()) release.mutate();
        }}
      >
        <FormField
          id="release-reason"
          label="Lý do"
          error={reason.trim() ? undefined : "Ghi lý do tiếp nhận"}
        >
          <Textarea
            id="release-reason"
            rows={3}
            maxLength={MAX_REASON}
            value={reason}
            onChange={(event) => setReason(event.target.value)}
          />
        </FormField>
      </form>
      <DialogFooter>
        <Button variant="outline" size="sm" onClick={onDone}>
          Hủy
        </Button>
        <Button
          type="submit"
          form="release-intake"
          size="sm"
          disabled={release.isPending || !reason.trim()}
        >
          {release.isPending ? "Đang gửi..." : "Gửi"}
        </Button>
      </DialogFooter>
    </>
  );
};

/** Tải trạng thái tiếp nhận mới nhất rồi mới cho gửi (backend kiểm tra revision / version). */
const ReleaseBody = ({ target, onDone }: { target: ReleaseTarget; onDone: () => void }) => {
  const intake = useIntake(target.workspace, target.id);
  if (intake.isPending) return <FieldsSkeleton rows={1} label="Đang tải tiếp nhận" />;
  if (intake.error) return <ErrorState error={intake.error} fallback="Không tải được tiếp nhận." />;
  if (!intake.data) return <p className="text-crm-text-2">Ticket không qua tiếp nhận.</p>;
  return <ReleaseForm target={target} state={intake.data} onDone={onDone} />;
};

/** Form tiếp nhận: điều phối viên chuyển ticket sang Chờ duyệt (backend bắt buộc lý do). */
export const ReleaseIntakeDialog = ({
  target,
  onClose,
}: {
  target: ReleaseTarget | null;
  onClose: () => void;
}) => (
  <Dialog open={target !== null} onOpenChange={(open) => !open && onClose()}>
    <DialogContent className="sm:max-w-120" aria-describedby={undefined}>
      <DialogHeader>
        <DialogTitle>Tiếp nhận ticket</DialogTitle>
      </DialogHeader>
      {/* Mỗi lần mở là form mới, mã thao tác mới */}
      {target && <ReleaseBody target={target} onDone={onClose} />}
    </DialogContent>
  </Dialog>
);
