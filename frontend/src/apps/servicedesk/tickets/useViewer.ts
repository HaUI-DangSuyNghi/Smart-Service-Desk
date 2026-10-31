import { PERMISSIONS, useActor, useMe, usePermissions } from "../../../platform/human/workspace";
import type { Viewer } from "./ticketPermissions";

/** Người đang xem: ai, có quyền xử lý/giao việc không, có phải siêu quản trị không. */
export const useViewer = (): Viewer => {
  const { actor } = useActor();
  const { can } = usePermissions();
  const me = useMe();
  return {
    subject: actor?.subject ?? null,
    canHandle: can(PERMISSIONS.taskHandle),
    canAssign: can(PERMISSIONS.taskAssign),
    isPlatformAdmin: me.data?.platformAdmin ?? false,
  };
};
