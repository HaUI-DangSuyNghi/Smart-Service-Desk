package vn.haui.dx.apps.servicedesk.intelligence.enrichment;

import org.springframework.stereotype.Service;
import vn.haui.dx.apps.servicedesk.ServiceDeskProcess;
import vn.haui.dx.apps.servicedesk.Ticket;
import vn.haui.dx.apps.servicedesk.TicketService;
import vn.haui.dx.apps.servicedesk.intelligence.context.ContextEvaluationGuard;
import vn.haui.dx.apps.servicedesk.intelligence.context.ContextReadinessPolicy;
import vn.haui.dx.apps.servicedesk.intelligence.context.ContextReadinessView;
import vn.haui.dx.apps.servicedesk.intelligence.context.ContextSlotExtractor;
import vn.haui.dx.hpdi.common.api.CapabilityException;
import vn.haui.dx.hpdi.human.api.HumanContext;
import vn.haui.dx.hpdi.process.api.ProcessObservations;
import vn.haui.dx.hpdi.process.api.ProcessSnapshot;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class EnrichmentLifecycleService {
    public static final String VERSION = "ticket-enrichment-lifecycle@0.1.0";

    public record View(
            String contractVersion, UUID ticketId, long contextRevision, String status, long version,
            int roundCount, boolean manualReviewRequired, boolean workflowGateApplied,
            boolean needsReview, boolean operationalAuthority,
            Instant deadlineAt, Instant remindedAt, UUID requestInstanceId, String deliveryStatus,
            String nextAction, List<String> reasonCodes, List<ContextReadinessView.Question> questions,
            ContextReadinessView readiness) {}

    private final TicketService tickets;
    private final EnrichmentCaseStore store;
    private final ContextEvaluationGuard guard;
    private final ContextSlotExtractor extractor;
    private final ContextReadinessPolicy policy;
    private final HumanContext human;
    private final ProcessObservations processes;
    private final EnrichmentConfiguration.Settings settings;
    private final vn.haui.dx.apps.servicedesk.intelligence.TicketContextualAssessmentService reassessment;
    private final EnrichmentMetrics metrics;

    public EnrichmentLifecycleService(
            TicketService tickets, EnrichmentCaseStore store, ContextEvaluationGuard guard,
            ContextSlotExtractor extractor, ContextReadinessPolicy policy, HumanContext human,
            ProcessObservations processes, EnrichmentConfiguration.Settings settings,
            vn.haui.dx.apps.servicedesk.intelligence.TicketContextualAssessmentService reassessment,
            EnrichmentMetrics metrics) {
        this.tickets = tickets;
        this.store = store;
        this.guard = guard;
        this.extractor = extractor;
        this.policy = policy;
        this.human = human;
        this.processes = processes;
        this.settings = settings;
        this.reassessment = reassessment;
        this.metrics = metrics;
    }

    public View get(String workspace, UUID id) {
        tickets.get(workspace, id);
        var state = store.find(workspace, id).orElseThrow(() -> CapabilityException.notFound("Enrichment case not found"));
        if (!settings.enabled() || state.readiness() == null) {
            return view(state, null, !settings.enabled());
        }
        try {
            return guard.evaluate(workspace, id, session -> {
                boolean fresh = !session.shadow() && state.contextRevision() == session.snapshot().contextRevision()
                        && session.snapshot().digest().equals(state.contextDigest()) && policy.digest().equals(state.policyDigest());
                return view(state, fresh ? state.readiness() : null, !fresh);
            });
        } catch (CapabilityException failure) {
            if (failure.code() == CapabilityException.Code.CONFLICT) {
                throw failure;
            }
            tickets.get(workspace, id);
            return view(state, null, true);
        }
    }

    /** Một command xử lý tối đa một generation; không chờ GPU trong transaction DB. */
    public View process(String workspace, UUID id) {
        tickets.get(workspace, id);
        if (!settings.enabled()) {
            return get(workspace, id);
        }
        var claimed = store.claim(workspace, id);
        if (claimed.isEmpty()) {
            return get(workspace, id);
        }
        var claim = claimed.get();
        long started = System.nanoTime();
        String outcome = "OTHER";
        try {
            var readiness = guard.evaluate(workspace, id, session -> {
                if (session.shadow()) {
                    throw CapabilityException.forbidden("Shadow observations cannot be published to lifecycle");
                }
                var before = processSnapshot(workspace, session.ticket());
                var result = policy.evaluate(session.snapshot(), extractor.extract(session.snapshot()), claim.previous().roundCount());
                if (result.overall() == ContextReadinessView.State.READINESS_OK
                        || result.overall() == ContextReadinessView.State.ENRICHMENT_OPTIONAL) {
                    var refreshed = reassessment.assess(workspace, id);
                    if (refreshed.contextRevision() != session.snapshot().contextRevision()
                            || !session.snapshot().digest().equals(refreshed.contextDigest())) {
                        throw CapabilityException.conflict("Reassessment revision changed");
                    }
                    if ("DEGRADED".equals(refreshed.assessmentStatus())) {
                        var reasons = new java.util.ArrayList<>(result.reasonCodes());
                        reasons.add("AI_PROVIDER_UNAVAILABLE");
                        result = new ContextReadinessView(ContextReadinessView.VERSION, id, result.contextRevision(),
                                result.contextDigest(), ContextReadinessView.State.MANUAL_REVIEW_REQUIRED, result.tasks(),
                                reasons, "COORDINATOR_REVIEW", List.of(), result.policyDigest(), true, false, false);
                    }
                }
                if (!java.util.Objects.equals(before, processSnapshot(workspace, session.ticket()))) {
                    throw CapabilityException.conflict("Process changed during evaluation");
                }
                return result;
            });
            var ticket = tickets.get(workspace, id);
            if (!store.complete(workspace, id, claim, readiness, open(ticket, processSnapshot(workspace, ticket)))) {
                store.fail(workspace, id, claim, "STALE_CONTEXT");
                outcome = "STALE_CONTEXT";
            } else {
                outcome = store.find(workspace, id).orElseThrow().status();
            }
        } catch (CapabilityException failure) {
            outcome = failure.code() == CapabilityException.Code.CONFLICT ? "STALE_CONTEXT" : "AI_PROCESSING_UNAVAILABLE";
            store.fail(workspace, id, claim, outcome);
        } catch (RuntimeException failure) {
            outcome = "AI_PROVIDER_UNAVAILABLE";
            store.fail(workspace, id, claim, "AI_PROVIDER_UNAVAILABLE");
        } finally {
            metrics.evaluated(outcome, System.nanoTime() - started);
        }
        return get(workspace, id);
    }

    public List<View> pending(String workspace, int page) {
        var actor = human.currentActor(workspace);
        if (actor == null || !workspace.equals(actor.workspace()) || !actor.hasRole("coordinator")) {
            throw CapabilityException.forbidden("Coordinator workspace role required");
        }
        if (page < 0 || page > 10000) {
            throw CapabilityException.validation("Page must be between 0 and 10000");
        }
        var result = store.pending(workspace, page).stream().map(state -> view(state, null, !settings.enabled())).toList();
        if (!actor.equals(human.currentActor(workspace))) {
            throw CapabilityException.forbidden("Actor changed before publication");
        }
        return result;
    }

    private View view(EnrichmentCase state, ContextReadinessView readiness, boolean unavailable) {
        var requestId = readiness == null ? null : store.currentRequest(
                state.workspace(), state.ticketId(), state.contextRevision()).orElse(null);
        var questions = requestId == null ? List.<ContextReadinessView.Question>of() : readiness.questions();
        boolean manual = state.manualRequired() || unavailable;
        String next = manual ? "COORDINATOR_REVIEW" : !questions.isEmpty() ? "ANSWER_CLARIFICATION"
                : state.status().equals("READY") ? "CONTINUE_ADVISORY" : "PROCESS_PENDING_CONTEXT";
        return new View(VERSION, state.ticketId(), state.contextRevision(), state.status(), state.version(), state.roundCount(),
                manual, false, true, false, state.deadlineAt(), state.remindedAt(), requestId, requestId == null ? "NOT_REQUESTED" : "PENDING",
                next, unavailable ? List.of("AI_PROCESSING_UNAVAILABLE") : state.reasonCodes(), questions, readiness);
    }

    private ProcessSnapshot processSnapshot(String workspace, Ticket ticket) {
        return processes.snapshots(workspace, Set.of(ticket.process().processInstanceId())).stream().findFirst().orElse(null);
    }

    private static boolean open(Ticket ticket, ProcessSnapshot state) {
        return state != null && state.historyPresent() && ticket.workspace().equals(state.workspace())
                && ticket.creator().equals(state.owner()) && ticket.id().toString().equals(state.businessKey())
                && ServiceDeskProcess.KEY.equals(state.processKey()) && state.endedAt() == null
                && state.deleteReason() == null && state.tasks().size() == 1 && state.tasks().get(0).active()
                && List.of("handle", "confirm").contains(state.tasks().get(0).key());
    }
}
