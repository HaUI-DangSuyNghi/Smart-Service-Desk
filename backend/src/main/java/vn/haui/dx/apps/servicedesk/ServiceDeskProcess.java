package vn.haui.dx.apps.servicedesk;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;
import vn.haui.dx.core.workflow.api.ProcessPolicy;
import vn.haui.dx.core.workflow.api.ProcessRegistration;
import static vn.haui.dx.core.identity.api.WorkspaceRole.*;

@Component
public class ServiceDeskProcess implements ProcessRegistration {
    public static final String KEY = "service-desk";

    @Override public List<ProcessPolicy> policies() {
        return List.of(new ProcessPolicy(KEY, Set.of(REQUESTER, AGENT, COORDINATOR), Set.of(),
            Map.of("handle", Set.of("resolved"), "confirm", Set.of("accepted", "rework")), Set.of(AGENT, COORDINATOR)));
    }
}
