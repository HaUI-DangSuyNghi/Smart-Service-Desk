import { useState, type ReactNode } from "react";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { IconDots, IconEdit, IconHistory, IconX, IconFlagCheck } from "@tabler/icons-react";
import { useCurrentWorkspace, usePeopleNames, personName } from "@/platform/human/workspace";
import {
  cancelRequest,
  closeRequest,
  updateRequest,
  type RequestManagement,
} from "@/platform/services/requestLifecycleService";
import { formatDateTime } from "@/platform/shared/lib/format";
import { Button } from "@/platform/shared/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from "@/platform/shared/ui/dialog";
import { ErrorState } from "@/platform/shared/ui/ErrorState";
import { FormField } from "@/platform/shared/ui/FormField";
import { Input } from "@/platform/shared/ui/input";
import { FieldsSkeleton } from "@/platform/shared/ui/Skeleton";
import { Textarea } from "@/platform/shared/ui/textarea";
import { toast } from "@/platform/shared/ui/toast";
import { useRequestManagement } from "./useRequestManagement";

export type RequestAction = "edit" | "cancel" | "close";
const LABELS = { edit: "Sửa", cancel: "Hủy", close: "Đóng" } as const;
const ICONS = { edit: IconEdit, cancel: IconX, close: IconFlagCheck };
const ACTION_VARIANTS = { edit: "info", cancel: "destructive", close: "success" } as const;
const allowed = (view: RequestManagement, action: RequestAction) =>
  action === "edit" ? view.editable : action === "cancel" ? view.cancellable : view.closable;

export const RequestChangeHistory = ({ view }: { view: RequestManagement }) => {
  const names = usePeopleNames(view.history.map((change) => change.actor));
  return (
    <section aria-label="Lịch sử thay đổi">
      <h3 className="mb-2 flex items-center gap-2 font-medium">
        <IconHistory size={16} />
        Lịch sử thay đổi
      </h3>
      {view.history.length === 0 && <p className="text-crm-text-3">Chưa có thay đổi nào.</p>}
      <ol className="space-y-3">
        {view.history.map((change) => (
          <li key={change.id} className="rounded-crm-sm border border-crm-line p-3 text-crm-sm">
            <p className="font-medium">
              {change.action === "CANCELLED"
                ? "Đã hủy yêu cầu"
                : change.action === "CLOSED"
                  ? "Đã đóng yêu cầu"
                  : "Đã sửa yêu cầu"}
              {" · "}
              {personName(names, change.actor)}
            </p>
            <time className="text-crm-text-3">{formatDateTime(change.createdAt)}</time>
            <p className="mt-1 whitespace-pre-wrap wrap-break-word">{change.reason}</p>
            {change.action === "UPDATED" &&
              view.fields
                .filter((field) => change.beforeValues[field.key] !== change.afterValues[field.key])
                .map((field) => (
                  <div key={field.key} className="mt-2 min-w-0">
                    <p className="font-medium">{field.label}</p>
                    <p className="whitespace-pre-wrap wrap-break-word text-crm-text-3">
                      Trước: {change.beforeValues[field.key]}
                    </p>
                    <p className="whitespace-pre-wrap wrap-break-word">
                      Sau: {change.afterValues[field.key]}
                    </p>
                  </div>
                ))}
          </li>
        ))}
      </ol>
    </section>
  );
};

/** Captures the version when the form opens; realtime refreshes preserve unsaved input. */
const RequestForm = ({
  view,
  action,
  onBack,
  onDone,
  reload,
  onPendingChange,
}: {
  view: RequestManagement;
  action: RequestAction;
  onBack: () => void;
  onDone: () => void;
  reload: () => void;
  onPendingChange: (pending: boolean) => void;
}) => {
  const { current } = useCurrentWorkspace();
  const client = useQueryClient();
  const [version] = useState(view.version);
  const [available] = useState(() => allowed(view, action));
  const [values, setValues] = useState(() =>
    Object.fromEntries(view.fields.map((f) => [f.key, f.value])),
  );
  const [reason, setReason] = useState("");
  const mutation = useMutation({
    onMutate: () => onPendingChange(true),
    onSettled: () => onPendingChange(false),
    mutationFn: () =>
      action === "cancel"
        ? cancelRequest(current!, view.processId, version, reason.trim())
        : action === "close"
          ? closeRequest(current!, view.processId, version, reason.trim())
          : updateRequest(current!, view.processId, version, values, reason.trim()),
    onSuccess: async () => {
      await client.invalidateQueries();
      toast.success(
        action === "cancel"
          ? "Đã hủy yêu cầu"
          : action === "close"
            ? "Đã đóng yêu cầu"
            : "Đã cập nhật yêu cầu",
      );
      onDone();
    },
  });
  if (!available)
    return <p role="alert">Yêu cầu đã thay đổi hoặc bạn không có quyền thực hiện thao tác này.</p>;
  return (
    <form
      className="space-y-4"
      onSubmit={(event) => {
        event.preventDefault();
        if (!mutation.isPending) mutation.mutate();
      }}
    >
      {action !== "edit" && (
        <p className="text-crm-text-2">
          {action === "cancel" ? "Hủy" : "Đóng"} sẽ kết thúc yêu cầu, dừng các công việc còn mở và
          giữ lại lịch sử.
          {action === "close" && " Người gửi xác nhận không cần tiếp tục xử lý ticket này."}
        </p>
      )}
      {action === "edit" &&
        view.fields.map((field) => {
          const props = {
            id: `request-${field.key}`,
            required: true,
            maxLength: field.maxLength,
            value: values[field.key] ?? "",
            disabled: mutation.isPending,
            onChange: (event: { target: { value: string } }) =>
              setValues((previous) => ({ ...previous, [field.key]: event.target.value })),
          };
          return (
            <FormField key={field.key} label={field.label} id={props.id}>
              {field.multiline ? <Textarea {...props} rows={5} /> : <Input {...props} />}
            </FormField>
          );
        })}
      <FormField label="Lý do" id="request-reason">
        <Textarea
          id="request-reason"
          required
          maxLength={1000}
          rows={3}
          value={reason}
          disabled={mutation.isPending}
          onChange={(event) => setReason(event.target.value)}
        />
      </FormField>
      {mutation.error && (
        <div>
          <ErrorState error={mutation.error} />
          <Button type="button" variant="outline" size="sm" onClick={reload}>
            Tải lại yêu cầu
          </Button>
        </div>
      )}
      <div className="flex justify-end gap-2">
        <Button type="button" variant="outline" disabled={mutation.isPending} onClick={onBack}>
          Quay lại
        </Button>
        <Button
          type="submit"
          variant={ACTION_VARIANTS[action]}
          disabled={mutation.isPending || !reason.trim()}
        >
          {mutation.isPending
            ? "Đang lưu…"
            : action === "cancel"
              ? "Xác nhận hủy"
              : action === "close"
                ? "Xác nhận đóng"
                : "Lưu thay đổi"}
        </Button>
      </div>
    </form>
  );
};

/** Shared dialog for list, board and detail actions. Policy always comes from the server. */
export const RequestActionDialog = ({
  processId,
  initialAction = "view",
  onClose,
  keepHistory = false,
}: {
  processId: string;
  initialAction?: RequestAction | "view";
  onClose: () => void;
  keepHistory?: boolean;
}) => {
  const query = useRequestManagement(processId);
  const [mode, setMode] = useState(initialAction);
  const [reloadKey, setReloadKey] = useState(0);
  const [busy, setBusy] = useState(false);
  return (
    <Dialog
      open
      onOpenChange={(open) => {
        if (!open && !busy) onClose();
      }}
    >
      <DialogContent
        showCloseButton={!busy}
        className="max-h-[90svh] overflow-y-auto sm:max-w-2xl"
        onInteractOutside={(event) => {
          if (busy) event.preventDefault();
        }}
        onEscapeKeyDown={(event) => {
          if (busy) event.preventDefault();
        }}
      >
        <DialogHeader>
          <DialogTitle>
            {mode === "view" ? "Quản lý yêu cầu" : `${LABELS[mode]} yêu cầu`}
          </DialogTitle>
          <DialogDescription>
            {query.data?.explanation ?? "Đang tải quyền thao tác của yêu cầu."}
          </DialogDescription>
        </DialogHeader>
        {query.isPending && <FieldsSkeleton rows={3} label="Đang tải yêu cầu" />}
        {query.error && <ErrorState error={query.error} />}
        {query.data &&
          (mode === "view" ? (
            <div className="flex flex-col gap-4">
              <div className="flex flex-wrap gap-2">
                {(["edit", "cancel", "close"] as const)
                  .filter((action) => allowed(query.data, action))
                  .map((action) => {
                    const Icon = ICONS[action];
                    return (
                      <Button
                        key={action}
                        size="sm"
                        variant={ACTION_VARIANTS[action]}
                        onClick={() => setMode(action)}
                      >
                        <Icon size={14} />
                        {LABELS[action]} yêu cầu
                      </Button>
                    );
                  })}
              </div>
              <RequestChangeHistory view={query.data} />
            </div>
          ) : (
            <RequestForm
              key={`${mode}/${reloadKey}`}
              view={query.data}
              action={mode}
              onPendingChange={setBusy}
              onBack={() => (keepHistory ? setMode("view") : onClose())}
              onDone={() => (keepHistory ? setMode("view") : onClose())}
              reload={() => {
                void query.refetch().then(() => {
                  if (keepHistory) setMode("view");
                  else setReloadKey((key) => key + 1);
                });
              }}
            />
          ))}
      </DialogContent>
    </Dialog>
  );
};

export const RequestActions = ({
  processId,
  compact = false,
  actions,
}: {
  processId: string;
  compact?: boolean;
  actions?: readonly RequestAction[];
}) => {
  const [mode, setMode] = useState<RequestAction | "view" | null>(null);
  if (actions?.length === 0) return null;
  let triggers: ReactNode;
  if (actions)
    triggers = actions.map((action) => {
      const Icon = ICONS[action];
      return (
        <Button
          key={action}
          variant={ACTION_VARIANTS[action]}
          size="sm"
          className={compact ? "w-full" : undefined}
          onClick={() => setMode(action)}
        >
          <Icon size={14} />
          {LABELS[action]}
        </Button>
      );
    });
  else
    triggers = (
      <Button
        variant="outline"
        size="sm"
        className={compact ? "w-full" : undefined}
        aria-label="Quản lý yêu cầu"
        onClick={() => setMode("view")}
      >
        <IconDots size={14} />
        {!compact && "Quản lý yêu cầu"}
      </Button>
    );
  return (
    <span
      className={compact ? "contents" : "inline-flex flex-wrap gap-2"}
      onClick={(event) => event.stopPropagation()}
      onKeyDown={(event) => event.stopPropagation()}
    >
      {triggers}
      {mode && (
        <RequestActionDialog
          processId={processId}
          initialAction={mode}
          keepHistory={!actions}
          onClose={() => setMode(null)}
        />
      )}
    </span>
  );
};
