package vn.haui.dx.core.workflow.internal;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;
import vn.haui.dx.core.workflow.api.ProcessPolicy;
import vn.haui.dx.core.workflow.api.ProcessRegistration;
import static vn.haui.dx.core.identity.api.WorkspaceRole.*;

public final class ApprovalProcesses implements ProcessRegistration {
    @Override
    public List<ProcessPolicy> policies() {
        return List.of(
            new ProcessPolicy("document-review", Set.of(REQUESTER, AGENT, COORDINATOR), Set.of("title"),
                Map.of("review", Set.of("approved", "rejected")), Set.of(), Set.of(COORDINATOR), true),
            new ProcessPolicy("equipment-approval", Set.of(REQUESTER, AGENT, COORDINATOR), Set.of("title"),
                Map.of("approve", Set.of("approved", "rejected"), "handover", Set.of("delivered")), Set.of(), Set.of(COORDINATOR), true));
    }
}
