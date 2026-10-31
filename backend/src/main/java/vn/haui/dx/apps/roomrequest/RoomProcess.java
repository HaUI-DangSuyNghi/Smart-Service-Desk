package vn.haui.dx.apps.roomrequest;
import java.util.*;
import org.springframework.stereotype.Component;
import vn.haui.dx.hpdi.process.api.*;
@Component
public class RoomProcess implements ProcessContribution {
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
