package vn.haui.dx.apps.servicedesk.intelligence.context;

import org.springframework.stereotype.Service;

import vn.haui.dx.apps.servicedesk.Ticket;
import vn.haui.dx.apps.servicedesk.TicketService;
import vn.haui.dx.apps.servicedesk.intelligence.duplicate.DuplicateCandidateProvider;
import vn.haui.dx.apps.servicedesk.intelligence.duplicate.DuplicateDecisionEngine;
import vn.haui.dx.hpdi.common.api.CapabilityException;
import vn.haui.dx.hpdi.human.api.Actor;
import vn.haui.dx.hpdi.human.api.HumanContext;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Guard độc lập trước xử lý và trước publication; không giữ DB transaction qua hai lần kiểm tra. */
@Service
public class ContextIntelligenceService {
    public static final String ENRICHMENT_VERSION = "ticket-enrichment@0.1.0";
    public static final String DUPLICATE_VERSION = "ticket-duplicate-probe@0.1.0";

    public record Disposition(boolean needsReview, boolean operationalAuthority, boolean autoMergeAllowed) {}

    public record Provenance(
            String contextDigest, String policyBundleDigest, Map<String, String> policyDigests,
            String candidateSetDigest, String retrieverIdentity, String modelDigest) {}

    public record EnrichmentView(
            String contractVersion, String status, String mode, UUID ticketId, long contextRevision,
            String readinessProfile, Map<String, ContextProjection.Slot> context, List<String> missingInfo,
            List<EnrichmentSignalPlanner.Signal> enrichmentSignals, List<String> questions,
            String ai07Status, Provenance provenance, Disposition disposition) {}

    public record CandidateView(
            UUID candidateRef, UUID canonicalTicketId, long contextRevision,
            DuplicateDecisionEngine.Result assessment) {}

    public record DuplicateView(
            String contractVersion, String status, String mode, UUID ticketId, long contextRevision,
            DuplicateDecisionEngine.Decision decision, UUID duplicateOf, List<CandidateView> candidates,
            int scanned, boolean candidateWindowFull, Provenance provenance, Disposition disposition) {}

    private record Guard(Ticket ticket, ContextSnapshot snapshot, Actor actor, String scope, String grantDigest) {}
    private record Ranked(DuplicateCandidateProvider.Candidate candidate, DuplicateDecisionEngine.Result result) {}

    private final TicketService tickets;
    private final ContextSnapshotAssembler snapshots;
    private final ContextSlotExtractor extractor;
    private final EnrichmentSignalPlanner planner;
    private final ContextIntelligenceConfiguration.Settings settings;
    private final ContextPolicyCatalog policy;
    private final ContextProcessingGate processing;
    private final HumanContext human;
    private final DuplicateCandidateProvider provider;
    private final DuplicateDecisionEngine decisions;

    public ContextIntelligenceService(
            TicketService tickets, ContextSnapshotAssembler snapshots, ContextSlotExtractor extractor,
            EnrichmentSignalPlanner planner, ContextIntelligenceConfiguration.Settings settings,
            ContextPolicyCatalog policy, ContextProcessingGate processing, HumanContext human,
            DuplicateCandidateProvider provider, DuplicateDecisionEngine decisions) {
        this.tickets = tickets;
        this.snapshots = snapshots;
        this.extractor = extractor;
        this.planner = planner;
        this.settings = settings;
        this.policy = policy;
        this.processing = processing;
        this.human = human;
        this.provider = provider;
        this.decisions = decisions;
    }

    public EnrichmentView enrich(String workspace, UUID id, String profile) {
        Guard guard = begin(workspace, id);
        var context = extractor.extract(guard.snapshot());
        var signals = planner.plan(context, profile);
        var questions = signals.stream().limit(3).map(EnrichmentSignalPlanner.Signal::question).toList();
        boolean shadow = shadow();
        var result = new EnrichmentView(
                ENRICHMENT_VERSION, shadow ? "SHADOW" : signals.isEmpty() ? "REVIEW_REQUIRED" : "NEEDS_ENRICHMENT",
                settings.mode().name(), id, guard.snapshot().contextRevision(), profile,
                shadow ? Map.of() : context.slots(),
                shadow ? List.of() : signals.stream().map(EnrichmentSignalPlanner.Signal::slot).toList(),
                shadow ? List.of() : signals, shadow ? List.of() : questions,
                guard.snapshot().contextRevision() > 0 ? "AI07_REFRESH_REQUIRED" : "AI07_UNCHANGED",
                shadow ? null : provenance(guard, null), disposition());
        verify(guard);
        return result;
    }

    public DuplicateView duplicates(String workspace, UUID id) {
        Guard guard = begin(workspace, id);
        var context = extractor.extract(guard.snapshot());
        var windowStart = provider.windowStart();
        var batch = provider.retrieve(workspace, id, windowStart);
        var query = new DuplicateDecisionEngine.Input(
                workspace, id, guard.ticket().createdAt(), context, null, false, true);
        var ranked = batch.candidates().stream().map(candidate -> new Ranked(candidate, decisions.decide(
                        query, new DuplicateDecisionEngine.Input(
                                workspace, candidate.row().id(), candidate.row().createdAt(), candidate.context(),
                                null, false, true))))
                .filter(value -> value.result().decision() != DuplicateDecisionEngine.Decision.DISTINCT)
                .sorted(Comparator.comparingInt((Ranked value) -> decisionPriority(value.result().decision()))
                        .thenComparing(Comparator.comparingInt((Ranked value) -> value.result().matched().size()).reversed())
                        .thenComparing(value -> value.candidate().row().id()))
                .limit(policy.displayLimit()).toList();
        boolean shadow = shadow();
        boolean reviewer = guard.actor().hasRole("agent", "coordinator", "assessment-worker");
        var candidates = shadow || !reviewer ? List.<CandidateView>of() : ranked.stream().map(value ->
                new CandidateView(value.candidate().row().id(), null,
                        value.candidate().snapshot().contextRevision(), value.result())).toList();
        var decision = shadow || ranked.isEmpty() ? DuplicateDecisionEngine.Decision.INSUFFICIENT_EVIDENCE
                : ranked.get(0).result().decision();
        var result = new DuplicateView(
                DUPLICATE_VERSION, shadow ? "SHADOW" : "REVIEW_REQUIRED", settings.mode().name(),
                id, guard.snapshot().contextRevision(), decision, null, candidates,
                shadow ? 0 : batch.scanned(), !shadow && batch.windowFull(),
                shadow ? null : provenance(guard, batch.digest()), disposition());

        // Requery cùng cửa sổ cố định để bắt revision, quyền và open-state đã thay đổi.
        var current = provider.retrieve(workspace, id, windowStart);
        if (!batch.digest().equals(current.digest())) {
            throw CapabilityException.conflict("Duplicate candidates changed during processing");
        }
        verify(guard);
        return result;
    }

    private Guard begin(String workspace, UUID id) {
        var ticket = tickets.getForIntelligence(workspace, id);
        var actor = human.currentActor(workspace);
        if (actor == null || !workspace.equals(actor.workspace()) || actor.subject() == null
                || actor.subject().isBlank() || !actor.hasRole("requester", "agent", "coordinator", "assessment-worker")) {
            throw CapabilityException.forbidden("Trusted Service Desk workspace actor required");
        }
        if (!settings.enabled() || settings.mode() == ContextIntelligenceConfiguration.Mode.DISABLED) {
            throw CapabilityException.notFound("AI08 is disabled");
        }
        if (settings.mode() == ContextIntelligenceConfiguration.Mode.PROMOTED) {
            throw CapabilityException.forbidden("AI08 promotion requires independent release approval");
        }
        String scope = actor.hasRole("agent", "coordinator", "assessment-worker")
                ? ContextProcessingGate.WORKSPACE_REVIEW : ContextProcessingGate.OWN_TICKET;
        String grant = processing.verify(workspace, scope);
        return new Guard(ticket, snapshots.read(workspace, id), actor, scope, grant);
    }

    private void verify(Guard guard) {
        String workspace = guard.snapshot().workspace();
        UUID id = guard.snapshot().ticketId();
        if (!guard.actor().equals(human.currentActor(workspace))) {
            throw CapabilityException.forbidden("Actor changed during processing");
        }
        var current = snapshots.read(workspace, id);
        if (!guard.ticket().equals(tickets.getForIntelligence(workspace, id))
                || !guard.snapshot().digest().equals(current.digest())
                || !guard.grantDigest().equals(processing.verify(workspace, guard.scope()))) {
            throw CapabilityException.conflict("Ticket context or processing permission changed");
        }
        if (!guard.actor().equals(human.currentActor(workspace))) {
            throw CapabilityException.forbidden("Actor changed before publication");
        }
    }

    private Provenance provenance(Guard guard, String candidateDigest) {
        return new Provenance(guard.snapshot().digest(), policy.bundleDigest(), policy.digests(),
                candidateDigest, "bounded-rule-first-cpu@0.1.0", null);
    }

    private boolean shadow() {
        return settings.mode() == ContextIntelligenceConfiguration.Mode.SHADOW;
    }

    private static Disposition disposition() {
        return new Disposition(true, false, false);
    }

    private static int decisionPriority(DuplicateDecisionEngine.Decision decision) {
        return switch (decision) {
            case MATCH_READY_FOR_REVIEW -> 0;
            case POSSIBLE_DUPLICATE -> 1;
            case RELATED_INCIDENT_POSSIBLE -> 2;
            default -> 3;
        };
    }
}
