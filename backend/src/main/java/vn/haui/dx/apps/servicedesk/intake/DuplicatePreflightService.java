package vn.haui.dx.apps.servicedesk.intake;

import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import vn.haui.dx.apps.servicedesk.intelligence.context.*;
import vn.haui.dx.apps.servicedesk.intelligence.duplicate.*;
import vn.haui.dx.hpdi.common.api.CapabilityException;

/** Stage-A CPU evidence. A model vote or a DRAFT policy cannot authorize a merge. */
@Service
public class DuplicatePreflightService {
    public record Candidate(UUID candidateRef, long contextRevision, String decision, boolean strongUnverified) {}
    public record View(String contractVersion, UUID ticketId, long contextRevision, String candidateSetDigest,
            int scanned, boolean candidateSetIncomplete, String retrievalCoverage, List<Candidate> candidates,
            boolean incidentSignal, int incidentCount, String policyDigest, boolean autoMergeAllowed, boolean operationalAuthority) {}

    private final ContextEvaluationGuard guard;
    private final ContextSlotExtractor extractor;
    private final DuplicateCandidateProvider provider;
    private final DuplicateDecisionEngine decisions;
    private final JdbcTemplate jdbc;
    private final String policyDigest;
    private final int incidentThreshold;
    private record Evaluation(View view, String room, String objectKey, String digest) {}

    public DuplicatePreflightService(ContextEvaluationGuard guard, ContextSlotExtractor extractor, DuplicateCandidateProvider provider,
            DuplicateDecisionEngine decisions, JdbcTemplate jdbc, ContextPolicyCatalog policy,
            @Value("${serviceDesk.intake.incident-threshold:3}") int incidentThreshold) {
        if (incidentThreshold < 3 || incidentThreshold > 100) throw new IllegalArgumentException("Incident threshold must be 3..100");
        this.guard = guard;
        this.extractor = extractor;
        this.provider = provider;
        this.decisions = decisions;
        this.jdbc = jdbc;
        this.incidentThreshold = incidentThreshold;
        this.policyDigest = ContextPolicyCatalog.digest("duplicate-preflight@1.0.0:500:50:30:" + incidentThreshold + ":" + policy.bundleDigest());
    }

    public String policyDigest() { return policyDigest; }

    public View probe(String workspace, UUID id) {
        Evaluation evaluation = guard.evaluate(workspace, id, session -> {
            var context = extractor.extract(session.snapshot());
            String room = value(context, "location.room");
            String object = objectKey(context);
            var start = provider.windowStartAt(session.ticket().createdAt());
            var batch = provider.retrievePreflight(workspace, id, start, room, object);
            var query = new DuplicateDecisionEngine.Input(workspace, id, session.ticket().createdAt(), context, null, false, true);
            boolean reviewer = session.actor().hasRole("coordinator", "agent", "assessment-worker");
            var candidates = batch.candidates().stream().map(candidate -> {
                var result = decisions.decide(query, new DuplicateDecisionEngine.Input(workspace, candidate.row().id(),
                        candidate.row().createdAt(), candidate.context(), null, false, true));
                return new Candidate(candidate.row().id(), candidate.snapshot().contextRevision(), result.decision().name(),
                        room != null && object != null && room.equals(value(candidate.context(), "location.room"))
                                && object.equals(objectKey(candidate.context())) && compatibleSymptoms(context, candidate.context()));
            }).filter(candidate -> !"DISTINCT".equals(candidate.decision())).limit(20).toList();
            String region = value(context, "location.building") != null ? value(context, "location.building") : room;
            var cohort = batch.candidates().stream().filter(candidate -> candidate.row().createdAt().isAfter(session.ticket().createdAt().minus(30, ChronoUnit.MINUTES)))
                    .filter(candidate -> region != null && (region.equals(value(candidate.context(), "location.building"))
                            || region.equals(value(candidate.context(), "location.room"))))
                    .filter(candidate -> compatibleSymptoms(context, candidate.context()))
                    .map(candidate -> objectKey(candidate.context()) == null ? "REPORT:" + candidate.row().id()
                            : value(candidate.context(), "location.room") + ":" + objectKey(candidate.context()))
                    .distinct().toList();
            String ownObject = object == null ? "REPORT:" + id : room + ":" + object;
            int incidentCount = region == null ? 0 : cohort.size() + (cohort.contains(ownObject) ? 0 : 1);
            if (!batch.digest().equals(provider.retrievePreflight(workspace, id, start, room, object).digest())) {
                throw CapabilityException.conflict("Duplicate candidates changed during preflight");
            }
            var view = new View("duplicate-preflight@1.0.0", id, session.snapshot().contextRevision(), session.shadow() ? null : batch.digest(),
                    session.shadow() ? 0 : batch.scanned(), !session.shadow() && (batch.windowFull() || session.ticket().createdAt().isBefore(start)), "BOUNDED_90_DAY_WINDOW",
                    session.shadow() || !reviewer ? List.of() : candidates, !session.shadow() && incidentCount >= incidentThreshold,
                    session.shadow() ? 0 : incidentCount, policyDigest, false, false);
            return new Evaluation(view, room, object, session.snapshot().digest());
        });
        var view = evaluation.view();
        if (view.candidateSetDigest() != null) {
            jdbc.update("insert into servicedesk_duplicate_context(ticket_id,workspace,context_revision,context_digest,room,object_key) "
                    + "select id,workspace,context_revision,?,?,? from servicedesk_ticket where workspace=? and id=? and context_revision=? "
                    + "on conflict(ticket_id) do update set context_revision=excluded.context_revision,context_digest=excluded.context_digest,"
                    + "room=excluded.room,object_key=excluded.object_key,updated_at=clock_timestamp()",
                    evaluation.digest(), evaluation.room(), evaluation.objectKey(), workspace, id, view.contextRevision());
        }
        return view;
    }

    public static String value(ContextProjection context, String slot) {
        return context.usable(slot) ? Objects.toString(context.slots().get(slot).value(), null) : null;
    }

    public static String objectKey(ContextProjection context) {
        String type = value(context, "device.type"), id = value(context, "device.identifier");
        return type == null || id == null ? null : type + ":" + id;
    }

    public static boolean compatibleSymptoms(ContextProjection first, ContextProjection second) {
        if (!first.usable("symptomCodes") || !second.usable("symptomCodes")) return false;
        var left = (List<?>) first.slots().get("symptomCodes").value();
        var right = (List<?>) second.slots().get("symptomCodes").value();
        return left.stream().anyMatch(right::contains);
    }
}
