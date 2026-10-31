package vn.haui.dx.apps.roomrequest;
import java.util.*;
import org.springframework.stereotype.Component;
import vn.haui.dx.hpdi.process.api.*;
@Component
public class RoomProcess implements ProcessContribution {
    private final org.springframework.beans.factory.ObjectProvider<ProcessCapability> processes;
    private final org.springframework.beans.factory.ObjectProvider<vn.haui.dx.hpdi.human.api.Notifications> notices;
    private final org.springframework.beans.factory.ObjectProvider<vn.haui.dx.hpdi.human.api.LiveUpdates> live;
    public RoomProcess(org.springframework.beans.factory.ObjectProvider<ProcessCapability> processes,
            org.springframework.beans.factory.ObjectProvider<vn.haui.dx.hpdi.human.api.Notifications> notices,
            org.springframework.beans.factory.ObjectProvider<vn.haui.dx.hpdi.human.api.LiveUpdates> live) {
        this.processes=processes; this.notices=notices; this.live=live;
    }
    @Override public void afterComplete(String workspace,String key,String processId,WorkItem completed,
            String outcome,List<WorkItem> active) {
        var process=processes.getObject().timeline(workspace,processId);
        boolean approved="approved".equals(outcome);
        notices.getObject().notify(new vn.haui.dx.hpdi.human.api.Notifications.Notice(workspace,
            approved ? "ROOM_REQUEST_APPROVED" : "ROOM_REQUEST_REJECTED",vn.haui.dx.hpdi.human.api.Notifications.Severity.INFO,
            approved ? "Yêu cầu đặt phòng đã được duyệt" : "Yêu cầu đặt phòng đã bị từ chối",
            process.variables().get("room"),"/roomrequest/requests/"+process.businessKey(),key,process.businessKey(),null,
            Set.of(process.initiator()),false,true));
        live.getObject().toUser(workspace,process.initiator(),"REQUEST_COMPLETED","request",processId);
    }
    public static final String KEY="room-request";
    // Không tự phục vụ: yêu cầu đặt phòng được tạo qua API của app
    public List<ProcessDefinition> definitions() {
        return List.of(new ProcessDefinition(KEY,Set.of("requester","agent","coordinator"),Set.of("room"),
            Map.of("approve",Set.of("approved","rejected")), Set.of("coordinator"), Set.of("coordinator"))
            .withLabels(ProcessLabels.named("Đặt phòng")
                .task("approve", "Duyệt đặt phòng")
                .outcome("approved", "Duyệt")
                .outcome("rejected", "Từ chối")
                .variable("room", "Phòng")));
    }
}
