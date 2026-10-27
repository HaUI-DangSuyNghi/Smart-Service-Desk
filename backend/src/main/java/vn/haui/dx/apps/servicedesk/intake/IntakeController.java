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

    public IntakeController(TicketService tickets, IntakeAdmission admission, HumanContext human) {
        this.tickets = tickets;
        this.admission = admission;
        this.human = human;
    }

    @GetMapping("/tickets/{id}/intake")
    public IntakeAdmission.View get(@PathVariable String workspace, @PathVariable UUID id) {
        tickets.get(workspace, id);
        var state = admission.find(workspace, id);
        if (state == null) throw CapabilityException.notFound("Intake admission not enabled for this ticket");
        // Never expose persisted AI evidence without current processing authorization.
        return new IntakeAdmission.View(state.contractVersion(), id, state.contextRevision(), state.version(), state.reviewRound(),
                state.status(), state.deadlineAt(), state.sealedRevision(), state.contextDigest(), state.policyDigest(),
                state.reason(), state.lateNotice(), null, null, null, true, false);
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
