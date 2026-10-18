package vn.haui.dx.apps.servicedesk;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;
import vn.haui.dx.hpdi.process.api.ProcessDefinition;
import vn.haui.dx.hpdi.process.api.ProcessContribution;


@Component
public class ServiceDeskProcess implements ProcessContribution {
    public static final String KEY = "service-desk";

    @Override public List<ProcessDefinition> definitions() {
        return List.of(new ProcessDefinition(KEY, Set.of("requester", "agent", "coordinator"), Set.of(),
            Map.of("handle", Set.of("resolved"), "confirm", Set.of("accepted", "rework"))));
    }
}
