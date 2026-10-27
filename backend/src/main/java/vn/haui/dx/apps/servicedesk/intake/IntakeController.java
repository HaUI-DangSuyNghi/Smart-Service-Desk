package vn.haui.dx.apps.servicedesk.intake;

import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.*;
import vn.haui.dx.apps.servicedesk.TicketService;
import vn.haui.dx.hpdi.common.api.CapabilityException;
import vn.haui.dx.hpdi.human.api.HumanContext;

@RestController
@RequestMapping("/api/apps/servicedesk/v1/workspaces/{workspace}")
public class IntakeController {
    private final TicketService tickets;
    private final IntakeAdmission admission;
    private final HumanContext human;
    private final IntakeCoordinator coordinator;

    public IntakeController(TicketService tickets, IntakeAdmission admission, HumanContext human, IntakeCoordinator coordinator) {
        this.tickets = tickets;
        this.admission = admission;
        this.human = human;
        this.coordinator = coordinator;
    }

    @GetMapping("/tickets/{id}/intake")
    public IntakeAdmission.View get(@PathVariable String workspace, @PathVariable UUID id) {
        tickets.get(workspace, id);
        return coordinator.read(workspace, id);
    }

    @PostMapping("/tickets/{id}/intake/evaluate")
    public IntakeAdmission.View evaluate(@PathVariable String workspace, @PathVariable UUID id) {
        tickets.get(workspace, id);
        coordinator.screen(workspace, id);
        return get(workspace, id);
    }

    @PostMapping("/tickets/{id}/intake/manual-release")
    public IntakeAdmission.View release(@PathVariable String workspace, @PathVariable UUID id,
            @RequestBody IntakeAdmission.Release request) {
        tickets.get(workspace, id);
        admission.manualRelease(workspace, id, request);
        return get(workspace, id);
    }

    @GetMapping("/intake-pending")
    public List<UUID> pending(@PathVariable String workspace) {
        var actor = human.currentActor(workspace);
        if (actor == null || !workspace.equals(actor.workspace()) || !actor.hasRole("coordinator")) {
            throw CapabilityException.forbidden("Coordinator workspace role required");
        }
        return admission.pending(workspace);
    }
}
