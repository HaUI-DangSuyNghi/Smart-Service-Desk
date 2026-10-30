import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
} from "../../../platform/shared/ui/dialog";
import { ErrorState } from "../../../platform/shared/ui/ErrorState";
import { FieldsSkeleton } from "../../../platform/shared/ui/Skeleton";
import { toast } from "../../../platform/shared/ui/toast";
import { CanonicalForm } from "./CanonicalForm";
import type { ReleaseTarget } from "./ReleaseIntakeDialog";
import { useIntake } from "./useIntake";

/** Tải trạng thái tiếp nhận mới nhất rồi mới cho gộp / tách (backend kiểm tra revision / version). */
const CanonicalBody = ({ target, onDone }: { target: ReleaseTarget; onDone: () => void }) => {
  const intake = useIntake(target.workspace, target.id);
  if (intake.isPending) return <FieldsSkeleton rows={2} label="Đang tải tiếp nhận" />;
  if (intake.error) return <ErrorState error={intake.error} fallback="Không tải được tiếp nhận." />;
  if (!intake.data) return <p className="text-crm-text-2">Ticket không qua tiếp nhận.</p>;
  return (
    <CanonicalForm
      workspace={target.workspace}
      ticketId={target.id}
      state={intake.data}
      onDone={() => {
        toast.success("Đã lưu");
        onDone();
      }}
    />
  );
};

/** Dò ticket trùng rồi gộp vào ticket gốc; ticket đang theo ticket gốc thì tách ra. */
export const CanonicalDialog = ({
  target,
  following,
  onClose,
}: {
  target: ReleaseTarget | null;
  following: boolean;
  onClose: () => void;
}) => (
  <Dialog open={target !== null} onOpenChange={(open) => !open && onClose()}>
    <DialogContent className="sm:max-w-140" aria-describedby={undefined}>
      <DialogHeader>
        <DialogTitle>{following ? "Tách khỏi ticket gốc" : "Kiểm tra ticket trùng"}</DialogTitle>
      </DialogHeader>
      {/* Mỗi lần mở là form mới, mã thao tác mới */}
      {target && <CanonicalBody target={target} onDone={onClose} />}
    </DialogContent>
  </Dialog>
);
