package vn.haui.dx.apps.servicedesk.intelligence.context;

import org.springframework.stereotype.Component;
import vn.haui.dx.apps.servicedesk.Ticket;
import vn.haui.dx.apps.servicedesk.TicketService;
import vn.haui.dx.hpdi.common.api.CapabilityException;
import vn.haui.dx.hpdi.human.api.Actor;
import vn.haui.dx.hpdi.human.api.HumanContext;

import java.util.UUID;
import java.util.function.Function;

/** Dùng cho contract mới; không thay đổi đường AI08/AI07 cũ. */
@Component
public class ContextEvaluationGuard {
    public record Session(Ticket ticket, ContextSnapshot snapshot, Actor actor, String scope, boolean shadow) {}

    private final TicketService tickets;
    private final ContextSnapshotAssembler snapshots;
    private final HumanContext human;
    private final ContextProcessingGate processing;
    private final ContextIntelligenceConfiguration.Settings settings;
    private final ContextReadinessPolicy policy;

    public ContextEvaluationGuard(
            TicketService tickets, ContextSnapshotAssembler snapshots, HumanContext human,
            ContextProcessingGate processing, ContextIntelligenceConfiguration.Settings settings,
            ContextReadinessPolicy policy) {
        this.tickets = tickets;
        this.snapshots = snapshots;
        this.human = human;
        this.processing = processing;
        this.settings = settings;
        this.policy = policy;
    }

    public <T> T evaluate(String workspace, UUID id, Function<Session, T> operation) {
        var ticket = tickets.getForIntelligence(workspace, id);
        var actor = human.currentActor(workspace);
        if (actor == null || !workspace.equals(actor.workspace()) || !actor.hasRole("requester", "agent", "coordinator", "assessment-worker")) {
            throw CapabilityException.forbidden("Service Desk workspace role required");
        }
        if (!settings.enabled() || settings.mode() == ContextIntelligenceConfiguration.Mode.DISABLED) {
            throw CapabilityException.notFound("Context intelligence is disabled");
        }
        if (settings.mode() == ContextIntelligenceConfiguration.Mode.PROMOTED) {
            throw CapabilityException.forbidden("Promotion requires independent release approval");
        }
        String scope = actor.hasRole("agent", "coordinator", "assessment-worker") ? ContextProcessingGate.WORKSPACE_REVIEW
                : ContextProcessingGate.OWN_TICKET;
        String grant = processing.verifyReadiness(workspace, scope, policy.digest());
        var snapshot = snapshots.read(workspace, id);
        var session = new Session(ticket, snapshot, actor, scope,
                settings.mode() == ContextIntelligenceConfiguration.Mode.SHADOW);
        T result = operation.apply(session);
        var current = snapshots.read(workspace, id);
        policy.verifyUnchanged();
        if (!ticket.equals(tickets.getForIntelligence(workspace, id)) || !snapshot.digest().equals(current.digest())
                || !grant.equals(processing.verifyReadiness(workspace, scope, policy.digest()))) {
            throw CapabilityException.conflict("Context or processing permission changed");
        }
        if (!actor.equals(human.currentActor(workspace))) {
            throw CapabilityException.forbidden("Actor changed before publication");
        }
        return result;
    }
}
