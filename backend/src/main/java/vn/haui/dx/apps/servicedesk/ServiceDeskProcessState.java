package vn.haui.dx.apps.servicedesk;

import java.util.Set;
import java.util.UUID;
import vn.haui.dx.hpdi.process.api.ProcessSnapshot;

/** Chính sách trạng thái mở dùng chung cho các tác vụ advisory của Service Desk. */
public final class ServiceDeskProcessState {
    private ServiceDeskProcessState() {}

    public static boolean isOpen(
            String workspace, UUID ticketId, String creator, String processId, ProcessSnapshot state) {
        if (state == null || !state.historyPresent()
                || !processId.equals(state.id()) || !workspace.equals(state.workspace())
                || !creator.equals(state.owner()) || !ticketId.toString().equals(state.businessKey())
                || state.endedAt() != null || state.deleteReason() != null
                || state.tasks().size() != 1 || !state.tasks().get(0).active()) {
            return false;
        }

        var taskKey = state.tasks().get(0).key();
        if (taskKey == null) {
            return false;
        }

        if (ServiceDeskProcess.KEY.equals(state.processKey())) {
            return Set.of("handle", "confirm").contains(taskKey);
        }
        return ServiceDeskProcess.REVIEWED_KEY.equals(state.processKey())
                && Set.of("review", "handle", "confirm").contains(taskKey);
    }
}
