package vn.haui.dx.apps.servicedesk;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;
import vn.haui.dx.hpdi.process.api.ProcessContribution;
import vn.haui.dx.hpdi.process.api.ProcessDefinition;
import vn.haui.dx.hpdi.process.api.ProcessLabels;

@Component
public class ServiceDeskProcess implements ProcessContribution {
    public static final String KEY = "service-desk";

    // Không tự phục vụ: process phải ra đời cùng ticket qua API của app
    @Override public List<ProcessDefinition> definitions() {
        return List.of(new ProcessDefinition(KEY, Set.of("requester", "agent", "coordinator"), Set.of(),
            Map.of("handle", Set.of("resolved"), "confirm", Set.of("accepted", "rework")),
            Set.of("agent", "coordinator"), Set.of("coordinator"))
            .withLabels(ProcessLabels.named("Yêu cầu hỗ trợ CNTT")
                .task("handle", "Xử lý yêu cầu")
                .task("confirm", "Xác nhận kết quả")
                .outcome("resolved", "Đã xử lý")
                .outcome("accepted", "Chấp nhận")
                .outcome("rework", "Yêu cầu làm lại")));
    }
}
