package vn.haui.dx.apps.samples;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;
import vn.haui.dx.hpdi.process.api.ProcessDefinition;
import vn.haui.dx.hpdi.process.api.ProcessContribution;


@Component
public final class ApprovalProcesses implements ProcessContribution {
    @Override
    public List<ProcessDefinition> definitions() {
        return List.of(
            new ProcessDefinition("document-review", Set.of("requester", "agent", "coordinator"), Set.of("title"),
                Map.of("review", Set.of("approved", "rejected"))),
            new ProcessDefinition("equipment-approval", Set.of("requester", "agent", "coordinator"), Set.of("title"),
                Map.of("approve", Set.of("approved", "rejected"), "handover", Set.of("delivered"))));
    }
}
