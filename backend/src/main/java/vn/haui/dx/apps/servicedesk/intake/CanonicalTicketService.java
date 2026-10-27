package vn.haui.dx.apps.servicedesk.intake;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import vn.haui.dx.apps.servicedesk.*;
import vn.haui.dx.apps.servicedesk.intelligence.context.*;
import vn.haui.dx.hpdi.common.api.CapabilityException;
import vn.haui.dx.hpdi.human.api.*;
import vn.haui.dx.hpdi.intelligence.api.BackgroundProcessing;
import vn.haui.dx.hpdi.process.api.*;

/** Human-approved R10 only. No similarity score or model vote can invoke this command. */
@Service
public class CanonicalTicketService {
    public record Link(UUID rootTicketId, long expectedContextRevision, long expectedRootRevision,
            long expectedIntakeVersion, String candidateSetDigest, String verifiedCategory, UUID operationId, String reason) {}
    public record Split(long expectedLinkVersion, long expectedContextRevision, UUID operationId, String reason) {}
    public record View(UUID ticketId, UUID rootTicketId, String status, String publicProgress, long version, String category) {}

    private final JdbcTemplate jdbc;
    private final TicketService tickets;
    private final IntakeAdmission admission;
    private final ContextEvaluationGuard guard;
    private final ContextSnapshotAssembler snapshots;
    private final ContextSlotExtractor extractor;
    private final ContextReadinessPolicy readiness;
    private final DuplicatePreflightService preflight;
    private final HumanContext human;
    private final ActivityJournal audit;
    private final ProcessObservations observations;
    private final ProcessCapability processes;
    private final WorkInbox inbox;
    private final BackgroundProcessing background;
    private final TransactionTemplate transaction;
    private final boolean enabled;
    private final boolean scheduled;
    private final List<String> workspaces;

    public CanonicalTicketService(JdbcTemplate jdbc, TicketService tickets, IntakeAdmission admission,
            ContextEvaluationGuard guard, ContextSnapshotAssembler snapshots, ContextSlotExtractor extractor,
            ContextReadinessPolicy readiness, DuplicatePreflightService preflight, HumanContext human, ActivityJournal audit,
            ProcessObservations observations, ProcessCapability processes, WorkInbox inbox, BackgroundProcessing background,
            PlatformTransactionManager manager, @Value("${serviceDesk.intake.canonical-human-enabled:false}") boolean enabled,
            @Value("${service-desk.background.scheduled:true}") boolean scheduled,
            @Value("${service-desk.background.workspaces:}") List<String> workspaces) {
        this.jdbc = jdbc; this.tickets = tickets; this.admission = admission; this.guard = guard; this.snapshots = snapshots;
        this.extractor = extractor; this.readiness = readiness; this.preflight = preflight; this.human = human; this.audit = audit;
        this.observations = observations; this.processes = processes; this.inbox = inbox; this.background = background;
        this.transaction = new TransactionTemplate(manager); this.enabled = enabled; this.scheduled = scheduled;
        this.workspaces = workspaces.stream().filter(value -> !value.isBlank()).distinct().toList();
    }

    @Transactional
    public View link(String workspace, UUID id, Link command) {
        var actor = coordinator(workspace);
        reason(command == null ? null : command.reason(), command == null ? null : command.operationId());
        if (!enabled) throw CapabilityException.forbidden("Human canonical linking is disabled pending policy approval");
        if (command.rootTicketId() == null || command.rootTicketId().equals(id)) throw CapabilityException.validation("Independent canonical root required");
        lock(id, command.rootTicketId());
        var follower = tickets.get(workspace, id);
        var root = tickets.get(workspace, command.rootTicketId());
        if (actor.subject().equals(follower.creator()) || actor.subject().equals(root.creator())) throw CapabilityException.forbidden("Independent coordinator required");
        String payload = ContextPolicyCatalog.digest(command.toString() + ":" + actor.subject());
        if (replayed(id, command.operationId(), payload)) return view(workspace, id);
        var state = admission.find(workspace, id);
        if (!ServiceDeskProcess.INTAKE_KEY.equals(follower.process().processKey()) || state == null || state.reviewTaskMayExist()
                || state.contextRevision() != command.expectedContextRevision() || state.version() != command.expectedIntakeVersion()
                || state.status().equals("CANONICAL_FOLLOWER") || state.status().equals("REVIEWED")) {
            throw CapabilityException.conflict("Only an untouched v2 intake can follow a canonical ticket");
        }
        if (Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from servicedesk_canonical_link where state='ACTIVE' "
                + "and (follower_id=? or follower_id=? or root_id=?))", Boolean.class, id, root.id(), id))) {
            throw CapabilityException.conflict("Canonical chains and cycles are prohibited");
        }
        guard.evaluate(workspace, id, session -> {
            var queryContext = extractor.extract(session.snapshot());
            var safety = readiness.evaluate(session.snapshot(), queryContext, 0);
            if (safety.reasonCodes().stream().anyMatch(code -> Set.of("SAFETY_ESCALATION", "CRITICAL_ESCALATION").contains(code))) {
                throw CapabilityException.conflict("Critical safety requires independent intake review");
            }
            var rootSnapshot = snapshots.read(workspace, root.id());
            if (rootSnapshot.contextRevision() != command.expectedRootRevision()) throw CapabilityException.conflict("Root context changed");
            var rootContext = extractor.extract(rootSnapshot);
            String room = DuplicatePreflightService.value(queryContext, "location.room");
            String object = DuplicatePreflightService.objectKey(queryContext);
            if (room == null || object == null || !room.equals(DuplicatePreflightService.value(rootContext, "location.room"))
                    || !object.equals(DuplicatePreflightService.objectKey(rootContext))
                    || !DuplicatePreflightService.compatibleSymptoms(queryContext, rootContext)) {
                throw CapabilityException.conflict("Exact room, physical object and compatible symptom evidence required");
            }
            var probe = preflight.probe(workspace, id);
            if (probe.candidateSetIncomplete() || !Objects.equals(probe.candidateSetDigest(), command.candidateSetDigest())
                    || probe.candidates().stream().noneMatch(candidate -> root.id().equals(candidate.candidateRef()) && candidate.strongUnverified())) {
                throw CapabilityException.conflict("Candidate evidence incomplete or changed; use independent review");
            }
            var review = jdbc.queryForList("select r.category,r.review_task_id,t.context_revision,t.awaiting_review "
                    + "from servicedesk_review r join servicedesk_ticket t on t.id=r.ticket_id where t.workspace=? and t.id=?", workspace, root.id());
            if (review.isEmpty() || !Objects.equals(command.verifiedCategory(), review.get(0).get("category"))
                    || Boolean.TRUE.equals(review.get(0).get("awaiting_review"))) throw CapabilityException.conflict("Current human-verified root category required");
            var rootAdmission = admission.find(workspace, root.id());
            if (rootAdmission == null ? rootSnapshot.contextRevision() != 0
                    : !"REVIEWED".equals(rootAdmission.status()) || !Objects.equals(rootAdmission.sealedRevision(), rootSnapshot.contextRevision())) {
                throw CapabilityException.conflict("Root category is not verified for this context revision");
            }
            var rootState = observations.snapshots(workspace, Set.of(root.process().processInstanceId())).stream().findFirst().orElse(null);
            if (!ServiceDeskProcessState.isOpen(workspace, root.id(), root.creator(), root.process().processInstanceId(), rootState)
                    || rootState.tasks().stream().noneMatch(task -> List.of("handle", "confirm").contains(task.key()))) {
                throw CapabilityException.conflict("Canonical root is no longer open for handling");
            }
            var task = processes.timeline(workspace, follower.process().processInstanceId()).currentTasks().stream()
                    .filter(value -> "intake".equals(value.key())).findFirst().orElseThrow(() -> CapabilityException.conflict("Intake task changed"));
            UUID decision = decision(id, root.id(), command.operationId(), "LINK", payload, actor.subject(), command.reason());
            jdbc.update("insert into servicedesk_canonical_link(follower_id,root_id,workspace,follower_revision,root_revision,category,decision_id) "
                    + "values (?,?,?,?,?,?,?) on conflict(follower_id) do update set root_id=excluded.root_id,state='ACTIVE',"
                    + "version=servicedesk_canonical_link.version+1,follower_revision=excluded.follower_revision,"
                    + "root_revision=excluded.root_revision,category=excluded.category,decision_id=excluded.decision_id,public_status='FOLLOWING'",
                    id, root.id(), workspace, state.contextRevision(), rootSnapshot.contextRevision(), command.verifiedCategory(), decision);
            jdbc.update("update servicedesk_intake_admission set status='CANONICAL_FOLLOWER',process_stage='follower',"
                    + "sealed_revision=context_revision,context_digest=?,review_task_id=?,version=version+1,lease_token=null,lease_until=null where ticket_id=?",
                    session.snapshot().digest(), task.id(), id);
            complete(workspace, task, "duplicate", command.reason());
            audit.record(workspace, new BusinessActivity("servicedesk.canonical.link", "servicedesk.ticket", id.toString(),
                    Map.of("rootTicketId", root.id().toString(), "decisionId", decision.toString())));
            return true;
        });
        return view(workspace, id);
    }

    @Transactional
    public View split(String workspace, UUID id, Split command) {
        var actor = coordinator(workspace);
        reason(command == null ? null : command.reason(), command == null ? null : command.operationId());
        var link = row(workspace, id);
        lock(id, (UUID) link.get("root_id"));
        var ticket = tickets.get(workspace, id);
        if (actor.subject().equals(ticket.creator())) throw CapabilityException.forbidden("Independent coordinator required");
        String payload = ContextPolicyCatalog.digest(command.toString() + ":" + actor.subject());
        if (replayed(id, command.operationId(), payload)) return view(workspace, id);
        link = row(workspace, id);
        var state = admission.find(workspace, id);
        if (!"ACTIVE".equals(link.get("state")) || ((Number) link.get("version")).longValue() != command.expectedLinkVersion()
                || state.contextRevision() != command.expectedContextRevision()) throw CapabilityException.conflict("Canonical generation changed");
        var task = processes.timeline(workspace, ticket.process().processInstanceId()).currentTasks().stream()
                .filter(value -> "follower".equals(value.key())).findFirst().orElseThrow(() -> CapabilityException.conflict("Follower task changed"));
        UUID decision = decision(id, (UUID) link.get("root_id"), command.operationId(), "SPLIT", payload, actor.subject(), command.reason());
        jdbc.update("update servicedesk_canonical_link set state='SPLIT',public_status='INDEPENDENT_INTAKE',version=version+1,decision_id=? where follower_id=?", decision, id);
        admission.rework(workspace, ticket.process().processInstanceId());
        jdbc.update("update servicedesk_intake_admission set review_task_id=? where ticket_id=?", task.id(), id);
        complete(workspace, task, "split", command.reason());
        audit.record(workspace, new BusinessActivity("servicedesk.canonical.split", "servicedesk.ticket", id.toString(), Map.of("decisionId", decision.toString())));
        return view(workspace, id);
    }

    public View view(String workspace, UUID id) {
        tickets.get(workspace, id);
        var link = row(workspace, id);
        boolean reviewer = human.currentActor(workspace).hasRole("coordinator", "agent");
        return new View(id, reviewer ? (UUID) link.get("root_id") : null, (String) link.get("state"),
                (String) link.get("public_status"), ((Number) link.get("version")).longValue(), (String) link.get("category"));
    }

    public void beforeLinkComplete(String workspace, String processId) {
        if (!Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from servicedesk_canonical_link l "
                + "join servicedesk_ticket t on t.id=l.follower_id join servicedesk_canonical_decision d on d.id=l.decision_id "
                + "where t.workspace=? and t.process_instance_id=? and l.state='ACTIVE' and d.action='LINK' "
                + "and l.follower_revision=t.context_revision and d.actor=?)", Boolean.class, workspace, processId, human.currentActor(workspace).subject()))) {
            throw CapabilityException.conflict("Canonical decision receipt required");
        }
    }

    public void beforeFollowerComplete(String workspace, String processId, String outcome) {
        var rows = jdbc.queryForList("select l.* from servicedesk_canonical_link l join servicedesk_ticket t on t.id=l.follower_id "
                + "where t.workspace=? and t.process_instance_id=?", workspace, processId);
        if (rows.isEmpty()) throw CapabilityException.conflict("Canonical link not found");
        var link = rows.get(0);
        UUID id = (UUID) link.get("follower_id");
        if ("split".equals(outcome)) {
            if (!"SPLIT".equals(link.get("state")) || !Boolean.TRUE.equals(jdbc.queryForObject(
                    "select exists(select 1 from servicedesk_canonical_decision where id=? and action='SPLIT' and actor=?)",
                    Boolean.class, link.get("decision_id"), human.currentActor(workspace).subject()))) throw CapabilityException.conflict("Split decision required");
            return;
        }
        if (!"closed".equals(outcome) || !"ACTIVE".equals(link.get("state")) || !rootClosed(workspace, link)) {
            throw CapabilityException.conflict("Canonical root is not confirmed closed or context requires human check");
        }
        jdbc.update("update servicedesk_canonical_link set state='CLOSED',public_status='CLOSED',version=version+1,updated_at=clock_timestamp() where follower_id=?", id);
        notice(workspace, id, ((Number) link.get("follower_revision")).longValue(), "CANONICAL_CLOSED");
        audit.record(workspace, new BusinessActivity("servicedesk.canonical.closed", "servicedesk.ticket", id.toString(), Map.of("rootTicketId", link.get("root_id").toString())));
    }

    public void rootConfirmed(String workspace, String processId) {
        var ids = jdbc.query("select id from servicedesk_ticket where workspace=? and process_instance_id=? for update",
                (row, index) -> row.getObject(1, UUID.class), workspace, processId);
        if (!ids.isEmpty()) jdbc.update("update servicedesk_canonical_link set public_status='ROOT_CONFIRMED',updated_at=clock_timestamp() "
                + "where workspace=? and root_id=? and state='ACTIVE'", workspace, ids.get(0));
    }

    @Scheduled(fixedDelayString = "${serviceDesk.intake.poll-ms:60000}")
    public void poll() { if (scheduled) workspaces.forEach(this::propagate); }

    public void propagate(String workspace) {
        var links = jdbc.queryForList("select * from servicedesk_canonical_link where workspace=? and state='ACTIVE' order by updated_at,follower_id limit 50", workspace);
        for (var link : links) {
            try {
                background.run(workspace, () -> transaction.executeWithoutResult(status -> {
                    UUID id = (UUID) link.get("follower_id");
                    lock(id, (UUID) link.get("root_id"));
                    var current = row(workspace, id);
                    if (!"ACTIVE".equals(current.get("state"))) return;
                    var ticket = tickets.getForIntelligence(workspace, id);
                    if (!rootClosed(workspace, current)) return;
                    var task = processes.timeline(workspace, ticket.process().processInstanceId()).currentTasks().stream()
                            .filter(value -> "follower".equals(value.key())).findFirst().orElseThrow();
                    complete(workspace, task, "closed", "Canonical root confirmed closed");
                }));
            } catch (RuntimeException unavailable) {
                // Keep ACTIVE for recovery; no impersonation, second process or silent closure.
            } finally {
                // Rotate unsuccessful work so blocked followers cannot starve later links.
                jdbc.update("update servicedesk_canonical_link set updated_at=clock_timestamp() where follower_id=? and state='ACTIVE'",
                        link.get("follower_id"));
            }
        }
    }

    private boolean rootClosed(String workspace, Map<String, Object> link) {
        UUID rootId = (UUID) link.get("root_id"), followerId = (UUID) link.get("follower_id");
        var root = tickets.getForIntelligence(workspace, rootId);
        var follower = admission.find(workspace, followerId);
        if (follower == null || follower.lateNotice() || follower.contextRevision() != ((Number) link.get("follower_revision")).longValue()) return false;
        var revision = jdbc.queryForObject("select context_revision from servicedesk_ticket where id=?", Long.class, rootId);
        if (revision != ((Number) link.get("root_revision")).longValue()) return false;
        var state = observations.snapshots(workspace, Set.of(root.process().processInstanceId())).stream().findFirst().orElse(null);
        return state != null && state.historyPresent() && state.endedAt() != null && state.deleteReason() == null
                && root.process().processInstanceId().equals(state.id()) && workspace.equals(state.workspace())
                && root.id().toString().equals(state.businessKey()) && root.creator().equals(state.owner())
                && "end".equals(state.endActivityId()) && state.tasks().isEmpty();
    }

    private void complete(String workspace, WorkItem task, String outcome, String reason) {
        var actor = human.currentActor(workspace);
        if (task.assignee() == null) inbox.claim(workspace, task.id());
        else if (!actor.subject().equals(task.assignee())) throw CapabilityException.conflict("Task already owned; coordinator recovery required");
        inbox.complete(workspace, task.id(), task.key(), outcome, reason);
    }
    private Actor coordinator(String workspace) {
        var actor = human.currentActor(workspace);
        if (actor == null || !workspace.equals(actor.workspace()) || !actor.hasRole("coordinator")) throw CapabilityException.forbidden("Coordinator required");
        return actor;
    }
    private void lock(UUID first, UUID second) {
        jdbc.queryForList("select id from servicedesk_ticket where id in (?,?) order by id for update", first, second);
    }
    private Map<String, Object> row(String workspace, UUID id) {
        var rows = jdbc.queryForList("select * from servicedesk_canonical_link where workspace=? and follower_id=?", workspace, id);
        if (rows.isEmpty()) throw CapabilityException.notFound("Canonical link not found");
        return rows.get(0);
    }
    private void reason(String reason, UUID operation) {
        if (operation == null || reason == null || reason.isBlank() || reason.length() > 1000 || reason.codePoints().anyMatch(Character::isISOControl)) {
            throw CapabilityException.validation("Operation ID and reason required");
        }
    }
    private UUID decision(UUID follower, UUID root, UUID operation, String action, String payload, String actor, String reason) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into servicedesk_canonical_decision(id,follower_id,root_id,operation_id,action,payload_digest,actor,reason) values (?,?,?,?,?,?,?,?)",
                id, follower, root, operation, action, payload, actor, reason);
        return id;
    }
    private boolean replayed(UUID id, UUID operation, String payload) {
        var rows = jdbc.queryForList("select d.payload_digest,d.id,l.decision_id,l.follower_revision,t.context_revision "
                + "from servicedesk_canonical_decision d join servicedesk_canonical_link l on l.follower_id=d.follower_id "
                + "join servicedesk_ticket t on t.id=d.follower_id where d.follower_id=? and d.operation_id=?", id, operation);
        if (rows.isEmpty()) return false;
        var row = rows.get(0);
        if (!payload.equals(row.get("payload_digest")) || !row.get("id").equals(row.get("decision_id"))
                || !row.get("follower_revision").equals(row.get("context_revision"))) {
            throw CapabilityException.conflict("Canonical operation payload or generation changed");
        }
        return true;
    }
    private void notice(String workspace, UUID id, long revision, String kind) {
        jdbc.update("insert into servicedesk_intake_notice(id,ticket_id,workspace,context_revision,kind) values (?,?,?,?,?) on conflict do nothing",
                UUID.randomUUID(), id, workspace, revision, kind);
    }
}
