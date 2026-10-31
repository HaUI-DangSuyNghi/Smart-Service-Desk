import { useActor } from "../../../platform/human/workspace";
import { RequestActions } from "../../../platform/process";
import type { RequestAction } from "../../../platform/process/requests/RequestActions";
import { useRequestManagement } from "../../../platform/process/requests/useRequestManagement";
import { ErrorState } from "../../../platform/shared/ui/ErrorState";
import { TableActions } from "../../../platform/shared/ui/TableActions";

export const RoomOwnerActions = ({
  processId,
  creator,
  compact = false,
}: {
  processId: string;
  creator: string;
  compact?: boolean;
}) => {
  const { actor } = useActor();
  const owner = actor?.subject === creator;
  const management = useRequestManagement(processId, owner);
  if (!owner) return null;
  if (management.error) return <ErrorState error={management.error} />;
  const actions: RequestAction[] = [];
  if (management.data?.editable) actions.push("edit");
  if (management.data?.cancellable) actions.push("cancel");
  if (actions.length === 0) return null;
  const buttons = <RequestActions processId={processId} actions={actions} compact={compact} />;
  return compact ? (
    <TableActions>{buttons}</TableActions>
  ) : (
    <div className="border-b border-crm-line px-4 py-3">{buttons}</div>
  );
};
