package vn.haui.dx.apps.samples;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;
import vn.haui.dx.hpdi.process.api.ProcessContribution;
import vn.haui.dx.hpdi.process.api.ProcessDefinition;
import vn.haui.dx.hpdi.process.api.ProcessLabels;

/** Hai quy trình mẫu không gắn bản ghi nghiệp vụ: tạo được từ form chung của Tầng 2. */
@Component
public final class ApprovalProcesses implements ProcessContribution {
    @Override
    public List<ProcessDefinition> definitions() {
        return List.of(
            // Key giữ "document-review" để không vỡ dữ liệu, test cũ; tên hiển thị là "Đề xuất chung"
            new ProcessDefinition("document-review", Set.of("requester", "agent", "coordinator"), Set.of("title", "content"),
                Map.of("review", Set.of("approved", "rejected")), Set.of(), Set.of("coordinator"))
                .allowSelfService()
                .withLabels(ProcessLabels.named("Đề xuất chung")
                    .task("review", "Xem xét đề xuất")
                    .outcome("approved", "Duyệt")
                    .outcome("rejected", "Từ chối")
                    .variable("title", "Tiêu đề")
                    .longVariable("content", "Nội dung")),
            new ProcessDefinition("equipment-approval", Set.of("requester", "agent", "coordinator"), Set.of("title"),
                Map.of("approve", Set.of("approved", "rejected"), "handover", Set.of("delivered")), Set.of(), Set.of("coordinator"))
                .allowSelfService()
                .withLabels(ProcessLabels.named("Mượn thiết bị")
                    .task("approve", "Duyệt yêu cầu")
                    .task("handover", "Bàn giao thiết bị")
                    .outcome("approved", "Duyệt")
                    .outcome("rejected", "Từ chối")
                    .outcome("delivered", "Đã bàn giao")
                    .variable("title", "Thiết bị cần mượn")));
    }
}
