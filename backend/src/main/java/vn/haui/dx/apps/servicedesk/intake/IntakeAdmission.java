package vn.haui.dx.apps.servicedesk.intake;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.haui.dx.apps.servicedesk.TicketContextChanged;
import vn.haui.dx.apps.servicedesk.intelligence.context.ContextPolicyCatalog;
import vn.haui.dx.apps.servicedesk.intelligence.context.ContextSnapshotAssembler;
import vn.haui.dx.hpdi.common.api.CapabilityException;
import vn.haui.dx.hpdi.human.api.ActivityJournal;
import vn.haui.dx.hpdi.human.api.BusinessActivity;
import vn.haui.dx.hpdi.human.api.HumanContext;

/** App-owned admission; existing Flowable review tasks can exist while admission waits. */
@Service
public class IntakeAdmission {
    public static final String VERSION = "intake-admission@1.0.0";

    public record View(String contractVersion, UUID ticketId, long contextRevision, long version,
            int reviewRound, String status, Instant deadlineAt, Long sealedRevision,
            String contextDigest, String policyDigest, String reason, boolean lateNotice,
            JsonNode readiness, JsonNode duplicatePreflight, JsonNode finalAssessment,
            boolean reviewTaskMayExist, boolean operationalAuthority) {}

    public record Release(long expectedContextRevision, long expectedVersion, UUID operationId, String reason) {}

    private final JdbcTemplate jdbc;
    private final HumanContext human;
    private final ActivityJournal audit;
    private final ObjectMapper json;
    private final ObjectProvider<ContextSnapshotAssembler> snapshots;
    private final ObjectProvider<vn.haui.dx.hpdi.process.api.ProcessCapability> processes;
    private final ObjectProvider<vn.haui.dx.hpdi.process.api.WorkInbox> inbox;
    private final boolean newTickets;
    private final long timeoutSeconds;
    private final String policyDigest;

    public IntakeAdmission(JdbcTemplate jdbc, HumanContext human, ActivityJournal audit, ObjectMapper json,
            ObjectProvider<ContextSnapshotAssembler> snapshots,
            ObjectProvider<vn.haui.dx.hpdi.process.api.ProcessCapability> processes,
            ObjectProvider<vn.haui.dx.hpdi.process.api.WorkInbox> inbox,
            @Value("${serviceDesk.intake.new-tickets:false}") boolean newTickets,
            @Value("${serviceDesk.intake.timeout-seconds:86400}") long timeoutSeconds) {
        if (timeoutSeconds < 60 || timeoutSeconds > 86400) {
            throw new IllegalArgumentException("Intake timeout must be between 60 and 86400 seconds");
        }
        this.jdbc = jdbc;
        this.human = human;
        this.audit = audit;
        this.json = json;
        this.snapshots = snapshots;
        this.processes = processes;
        this.inbox = inbox;
        this.newTickets = newTickets;
        this.timeoutSeconds = timeoutSeconds;
        this.policyDigest = ContextPolicyCatalog.digest(VERSION + ":" + timeoutSeconds);
    }

    public String policyDigest() { return policyDigest; }

    @EventListener
    @Transactional
    public void changed(TicketContextChanged event) {
        var ticket = lockTicket(event.workspace(), event.ticketId());
        if (newTickets && event.contextRevision() == 0) {
            jdbc.update("insert into servicedesk_intake_admission(ticket_id,workspace,context_revision,deadline_at,policy_digest,process_stage) "
                    + "values (?,?,0,clock_timestamp()+(? * interval '1 second'),?,?) on conflict do nothing",
                    event.ticketId(), event.workspace(), timeoutSeconds, policyDigest,
                    vn.haui.dx.apps.servicedesk.ServiceDeskProcess.INTAKE_KEY.equals(ticket.get("process_key")) ? "intake" : "review");
        }
        var state = find(event.workspace(), event.ticketId());
        if (state == null || event.contextRevision() <= state.contextRevision()) return;

        boolean reviewed = List.of("REVIEWED", "CANONICAL_FOLLOWER").contains(state.status());
        if (reviewed) {
            jdbc.update("update servicedesk_intake_admission set context_revision=?,late_notice=true,version=version+1,"
                    + "reason=case when status='CANONICAL_FOLLOWER' then 'CANONICAL_CONTEXT_CHANGED' else 'REVIEW_STALE_NOTICE' end,"
                    + "updated_at=clock_timestamp() where ticket_id=?",
                    event.contextRevision(), event.ticketId());
        } else {
            jdbc.update("update servicedesk_intake_admission set context_revision=?,status='NEW',version=version+1,"
                    + "sealed_revision=null,context_digest=null,readiness_json=null,duplicate_json=null,final_json=null,"
                    + "attempt_key=null,lease_token=null,lease_until=null,updated_at=clock_timestamp() where ticket_id=?",
                    event.contextRevision(), event.ticketId());
            jdbc.update("update servicedesk_final_attempt set state='STALE',finished_at=clock_timestamp() "
                    + "where ticket_id=? and state='RUNNING'", event.ticketId());
        }
    }

    public View find(String workspace, UUID id) {
        return jdbc.query("select * from servicedesk_intake_admission where workspace=? and ticket_id=?", (row, index) ->
                new View(VERSION, id, row.getLong("context_revision"), row.getLong("version"), row.getInt("review_round"),
                        row.getString("status"), row.getTimestamp("deadline_at").toInstant(),
                        row.getObject("sealed_revision", Long.class), row.getString("context_digest"), row.getString("policy_digest"),
                        row.getString("reason"), row.getBoolean("late_notice"), decode(row.getString("readiness_json")),
                        decode(row.getString("duplicate_json")), decode(row.getString("final_json")), "review".equals(row.getString("process_stage")), false),
                workspace, id).stream().findFirst().orElse(null);
    }

    @Transactional
    public View manualRelease(String workspace, UUID id, Release request) {
        var actor = human.currentActor(workspace);
        if (actor == null || !workspace.equals(actor.workspace()) || !actor.hasRole("coordinator")) {
            throw CapabilityException.forbidden("Coordinator workspace role required");
        }
        if (request == null || request.operationId() == null || request.reason() == null
                || request.reason().isBlank() || request.reason().length() > 1000
                || request.reason().codePoints().anyMatch(Character::isISOControl)) {
            throw CapabilityException.validation("Release requires operationId and reason");
        }
        var ticket = lockTicket(workspace, id);
        if (actor.subject().equals(ticket.get("creator"))) {
            throw CapabilityException.forbidden("Independent coordinator required");
        }
        var state = find(workspace, id);
        if (state == null) throw CapabilityException.notFound("Intake admission not found");
        String payload = ContextPolicyCatalog.digest(request.toString() + ":" + actor.subject());
        var replay = jdbc.queryForList("select payload_digest,context_revision,review_round from servicedesk_intake_receipt "
                + "where ticket_id=? and operation_id=?", id, request.operationId());
        if (!replay.isEmpty()) {
            var receipt = replay.get(0);
            if (!payload.equals(receipt.get("payload_digest"))
                    || state.contextRevision() != ((Number) receipt.get("context_revision")).longValue()
                    || state.reviewRound() != ((Number) receipt.get("review_round")).intValue()) {
                throw CapabilityException.conflict("Release operation has a different payload or generation");
            }
            return state;
        }
        boolean trueWait = vn.haui.dx.apps.servicedesk.ServiceDeskProcess.INTAKE_KEY.equals(ticket.get("process_key")) && !state.reviewTaskMayExist();
        if ((!Boolean.TRUE.equals(ticket.get("awaiting_review")) && !trueWait) || "REVIEWED".equals(state.status())
                || "CANONICAL_FOLLOWER".equals(state.status()) || ("REVIEW_RELEASED".equals(state.status()) && !trueWait)
                || state.contextRevision() != request.expectedContextRevision() || state.version() != request.expectedVersion()) {
            throw CapabilityException.conflict("Intake generation changed or review already admitted");
        }
        var snapshot = snapshots.getObject().read(workspace, id);
        if (snapshot.contextRevision() != request.expectedContextRevision()) {
            throw CapabilityException.conflict("Context changed before release");
        }
        var task = processes.getObject().timeline(workspace, ticket.get("process_instance_id").toString())
                .currentTasks().stream().filter(value -> (trueWait ? "intake" : "review").equals(value.key())).findFirst()
                .orElseThrow(() -> CapabilityException.conflict("Process is not awaiting intake or review"));
        String taskId = task.id();
        jdbc.update("update servicedesk_intake_admission set status='REVIEW_RELEASED',sealed_revision=context_revision,"
                + "context_digest=?,reason=?,review_task_id=?,version=version+1,lease_token=null,lease_until=null,updated_at=clock_timestamp() "
                + "where ticket_id=?", snapshot.digest(), request.reason(), taskId, id);
        receipt(id, request.operationId(), "MANUAL_RELEASE", state, snapshot.digest(), payload, actor.subject(), request.reason(), taskId);
        audit.record(workspace, new BusinessActivity("servicedesk.intake.release", "servicedesk.ticket", id.toString(),
                Map.of("operationId", request.operationId().toString(), "contextVersion", Long.toString(state.contextRevision()),
                        "reviewRoundVersion", Integer.toString(state.reviewRound()))));
        if (trueWait) {
            if (task.assignee() == null) inbox.getObject().claim(workspace, taskId);
            inbox.getObject().complete(workspace, taskId, "intake", "ready", request.reason());
            String reviewId = processes.getObject().timeline(workspace, ticket.get("process_instance_id").toString()).currentTasks().stream()
                    .filter(value -> "review".equals(value.key())).findFirst().orElseThrow().id();
            jdbc.update("update servicedesk_intake_admission set process_stage='review',review_task_id=?,version=version+1 where ticket_id=?", reviewId, id);
            jdbc.update("update servicedesk_ticket set awaiting_review=true where id=?", id);
        }
        return find(workspace, id);
    }

    public void beforeIntakeComplete(String workspace, String processId, String taskId, String outcome) {
        if (!"ready".equals(outcome) || !Boolean.TRUE.equals(jdbc.queryForObject(
                "select exists(select 1 from servicedesk_intake_admission a join servicedesk_ticket t on t.id=a.ticket_id "
                + "join servicedesk_intake_receipt r on r.ticket_id=a.ticket_id where t.workspace=? and t.process_instance_id=? "
                + "and a.status='REVIEW_RELEASED' and a.sealed_revision=t.context_revision and a.review_task_id=? "
                + "and r.action='MANUAL_RELEASE' and r.review_task_id=? and r.context_revision=t.context_revision and r.review_round=a.review_round)",
                Boolean.class, workspace, processId, taskId, taskId))) {
            throw CapabilityException.conflict("Use the authorized intake disposition API");
        }
    }

    /** Called under the same ticket lock and transaction as the authoritative review. */
    public void approveReview(String workspace, UUID id, Long revision, Long version, String taskId) {
        var state = find(workspace, id);
        if (state == null) return;
        if (revision == null || version == null || revision != state.contextRevision() || version != state.version()
                || !"REVIEW_RELEASED".equals(state.status()) || !revision.equals(state.sealedRevision())
                || !taskId.equals(jdbc.queryForObject("select review_task_id from servicedesk_intake_admission where ticket_id=?", String.class, id))) {
            throw CapabilityException.conflict("Review requires the current released intake revision and version");
        }
        jdbc.update("update servicedesk_intake_admission set status='REVIEWED',version=version+1 where ticket_id=?", id);
    }

    public void beforeComplete(String workspace, String processId, String taskId) {
        if (Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from servicedesk_intake_admission a "
                + "join servicedesk_ticket t on t.id=a.ticket_id where t.workspace=? and t.process_instance_id=? "
                + "and (a.status<>'REVIEWED' or a.sealed_revision is distinct from t.context_revision "
                + "or a.review_task_id is distinct from ?))", Boolean.class, workspace, processId, taskId))) {
            throw CapabilityException.conflict("Review has not passed current intake admission");
        }
    }

    public void rework(String workspace, String processId) {
        jdbc.update("update servicedesk_intake_admission a set status='NEW',review_round=review_round+1,version=a.version+1,"
                + "sealed_revision=null,context_digest=null,final_json=null,readiness_json=null,duplicate_json=null,"
                + "attempt_key=null,lease_token=null,lease_until=null,late_notice=false,reason='REWORK',"
                + "process_stage=case when t.process_key='service-desk-intake-reviewed' then 'intake' else 'review' end,"
                + "deadline_at=clock_timestamp()+(? * interval '1 second') from servicedesk_ticket t "
                + "where t.id=a.ticket_id and t.workspace=? and t.process_instance_id=?", timeoutSeconds, workspace, processId);
    }

    public void handover(String workspace, String processId, String reviewId, String actor, String reason, String note) {
        var tickets = jdbc.queryForList("select id,context_revision from servicedesk_ticket where workspace=? and process_instance_id=? for update", workspace, processId);
        if (tickets.isEmpty()) throw CapabilityException.notFound("Ticket not found");
        UUID id = (UUID) tickets.get(0).get("id");
        var state = find(workspace, id);
        if (state == null) return;
        if (!"REVIEWED".equals(state.status()) || state.sealedRevision() == null
                || state.sealedRevision().longValue() != ((Number) tickets.get(0).get("context_revision")).longValue())
            throw CapabilityException.conflict("Handover requires the current approved intake revision");
        jdbc.update("update servicedesk_intake_admission set status='REVIEW_RELEASED',review_round=review_round+1,version=version+1,"
            + "review_task_id=?,process_stage='review',reason='HANDOVER',updated_at=clock_timestamp() where ticket_id=?", reviewId, id);
        var current = find(workspace, id);
        receipt(id, UUID.randomUUID(), "HANDOVER", current, current.contextDigest(),
            ContextPolicyCatalog.digest(reviewId + ":" + reason + ":" + note), actor, note, reviewId);
    }

    public void feedback(String workspace, UUID id, String taskId, Object decision, String actor, String reason) {
        var state = find(workspace, id);
        if (state == null) return;
        try {
            jdbc.update("insert into servicedesk_ai_feedback(id,ticket_id,review_round,context_revision,review_task_id,"
                    + "proposal,human_decision,actor,reason) values (?,?,?,?,?,cast(? as jsonb),cast(? as jsonb),?,?)",
                    UUID.randomUUID(), id, state.reviewRound(), state.contextRevision(), taskId,
                    state.finalAssessment() == null ? null : state.finalAssessment().toString(),
                    json.writeValueAsString(decision), actor, reason);
        } catch (java.io.IOException failure) {
            throw new IllegalStateException("Cannot record review feedback", failure);
        }
    }

    @Scheduled(fixedDelayString = "${serviceDesk.intake.poll-ms:60000}")
    public void expire() {
        jdbc.update("update servicedesk_intake_admission set status='MANUAL_REQUIRED',reason='INTAKE_TIMEOUT',"
                + "version=version+1,lease_token=null,lease_until=null,updated_at=clock_timestamp() "
                + "where deadline_at<=clock_timestamp() and status in ('NEW','NEEDS_ENRICHMENT','DUPLICATE_HOLD',"
                + "'FINAL_ASSESSMENT_QUEUED','ASSESSING')");
    }

    public List<UUID> pending(String workspace) {
        return pending(workspace, 0, 50);
    }

    public List<UUID> pending(String workspace, int page, int size) {
        if (page < 0 || page > 100000 || size < 1 || size > 100) throw CapabilityException.validation("Invalid page");
        return jdbc.query("select ticket_id from servicedesk_intake_admission where workspace=? "
                + "and (status not in ('REVIEWED','REVIEW_RELEASED','CANONICAL_FOLLOWER') "
                + "or (status='REVIEW_RELEASED' and process_stage='intake')) "
                + "order by deadline_at,ticket_id limit ? offset ?", (row, index) -> row.getObject(1, UUID.class), workspace, size, (long) page * size);
    }

    private Map<String, Object> lockTicket(String workspace, UUID id) {
        var rows = jdbc.queryForList("select creator,awaiting_review,process_instance_id,process_key from servicedesk_ticket where workspace=? and id=? for update", workspace, id);
        if (rows.isEmpty()) throw CapabilityException.notFound("Ticket not found");
        return rows.get(0);
    }

    private JsonNode decode(String value) {
        try { return value == null ? null : json.readTree(value); }
        catch (java.io.IOException failure) { throw new IllegalStateException("Invalid persisted intake data", failure); }
    }

    private void receipt(UUID id, UUID operation, String action, View state, String digest, String payload, String actor, String reason, String taskId) {
        jdbc.update("insert into servicedesk_intake_receipt(id,ticket_id,operation_id,action,context_revision,review_round,"
                + "context_digest,policy_digest,payload_digest,actor,reason,final_json,review_task_id) values (?,?,?,?,?,?,?,?,?,?,?,cast(? as jsonb),?)",
                UUID.randomUUID(), id, operation, action, state.contextRevision(), state.reviewRound(), digest,
                state.policyDigest(), payload, actor, reason, state.finalAssessment() == null ? null : state.finalAssessment().toString(), taskId);
    }
}
