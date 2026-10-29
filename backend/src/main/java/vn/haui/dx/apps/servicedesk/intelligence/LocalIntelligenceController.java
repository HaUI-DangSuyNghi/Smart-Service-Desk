package vn.haui.dx.apps.servicedesk.intelligence;

import org.springframework.web.bind.annotation.*;
import vn.haui.dx.apps.servicedesk.TicketService;
import vn.haui.dx.hpdi.common.api.CapabilityException;
import vn.haui.dx.hpdi.human.api.HumanContext;
import java.util.UUID;

@RestController
@RequestMapping("/api/apps/servicedesk/v1/workspaces/{workspace}")
public final class LocalIntelligenceController {
    private final LocalModelRuntime runtime;
    private final HumanContext human;
    private final TicketService tickets;
    private final BackgroundTicketAssessment assessments;
    private final String triageUrl;
    private final vn.haui.dx.apps.servicedesk.assistance.TicketAssistanceService assistance;
    private final vn.haui.dx.apps.servicedesk.assistance.ServiceDeskAssistanceConfiguration.Settings assistanceSettings;

    public LocalIntelligenceController(LocalModelRuntime runtime, HumanContext human, TicketService tickets,
            BackgroundTicketAssessment assessments,
            @org.springframework.beans.factory.annotation.Value("${dx.intelligence.triage.base-url:}") String triageUrl,
            vn.haui.dx.apps.servicedesk.assistance.TicketAssistanceService assistance,
            vn.haui.dx.apps.servicedesk.assistance.ServiceDeskAssistanceConfiguration.Settings assistanceSettings) {
        this.runtime = runtime; this.human = human; this.tickets = tickets; this.assessments = assessments;
        this.triageUrl = triageUrl;
        this.assistance = assistance; this.assistanceSettings = assistanceSettings;
    }

    public record Status(String mode, String engine, boolean modelEnabled, String assistanceMode) {}

    @GetMapping("/intelligence/status")
    public Status status(@PathVariable String workspace) {
        var actor = human.currentActor(workspace);
        if (actor == null || !workspace.equals(actor.workspace()) || !actor.hasRole("requester", "agent", "coordinator")) {
            throw CapabilityException.forbidden("Service Desk workspace role required");
        }
        boolean modelEnabled = runtime.supports(workspace);
        String engine = modelEnabled ? "hybrid" : runtime.enabled() || triageUrl.isBlank() ? "disabled" : "rules";
        String assistanceMode = !assistance.availableFor(workspace) ? "disabled"
                : "local-api".equals(assistanceSettings.mode()) ? "api" : "grounded";
        return new Status(runtime.enabled() ? "LOCAL_DEV" : "EXTERNAL", engine, modelEnabled, assistanceMode);
    }

    @PostMapping("/tickets/{id}/assessment/retry")
    public StoredAssessment retry(@PathVariable String workspace, @PathVariable UUID id) {
        return assessments.retry(tickets.get(workspace, id));
    }
}
