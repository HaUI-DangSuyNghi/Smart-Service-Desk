package vn.haui.dx.apps.servicedesk.intelligence.enrichment;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import vn.haui.dx.apps.servicedesk.intelligence.context.ContextReadinessPolicy;
import vn.haui.dx.apps.servicedesk.intelligence.context.ContextReadinessView;
import vn.haui.dx.hpdi.common.api.CapabilityException;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Chỉ lưu metadata advisory; thứ tự khóa luôn ticket → case → outbox. */
@Repository
public class EnrichmentCaseStore {
    public record Claim(UUID eventId, UUID claimId, long revision, long caseVersion, EnrichmentCase previous) {}

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final Clock clock;
    private final ContextReadinessPolicy policy;
    private final vn.haui.dx.hpdi.human.api.ActivityJournal audit;

    public EnrichmentCaseStore(JdbcTemplate jdbc, ObjectMapper json, Clock clock, ContextReadinessPolicy policy,
            vn.haui.dx.hpdi.human.api.ActivityJournal audit) {
        this.jdbc = jdbc;
        this.json = json;
        this.clock = clock;
        this.policy = policy;
        this.audit = audit;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void enqueue(String workspace, UUID id, long revision) {
        Instant now = clock.instant();
        lockTicket(workspace, id, false);
        jdbc.update("insert into servicedesk_enrichment_case "
                        + "(workspace,ticket_id,context_revision,policy_digest,status,deadline_at,updated_at) "
                        + "values (?,?,?,?,'NEW',?,?) on conflict (ticket_id) do nothing",
                workspace, id, revision, policy.digest(), time(now.plusSeconds(policy.waitSeconds())), time(now));
        var previous = findLocked(workspace, id).orElseThrow();
        boolean late = previous.lateAnswer() || previous.status().equals("TIMED_OUT")
                || previous.status().equals("MANUAL_REVIEW") || previous.status().equals("CANCELLED_BY_PROCESS")
                || revision > 0 && previous.deadlineAt() != null && !previous.deadlineAt().isAfter(now);
        jdbc.update("update servicedesk_enrichment_case set context_revision=?,context_digest=null,"
                        + "policy_digest=?,status=?,late_answer=?,manual_required=manual_required or ?,"
                        + "readiness_json=null,version=version+1,updated_at=? where workspace=? and ticket_id=?",
                revision, policy.digest(), revision == 0 ? "NEW" : "REASSESS_QUEUED", late, late,
                time(now), workspace, id);
        if (revision > 0 && !late) {
            // Câu trả lời đã đến đúng hạn: deadline cũ không được timeout công việc đánh giá lại.
            jdbc.update("update servicedesk_enrichment_case set deadline_at=?,reminder_due_at=null,reminded_at=null "
                            + "where workspace=? and ticket_id=?",
                    time(now.plusSeconds(120)), workspace, id);
        }
        jdbc.update("update servicedesk_enrichment_question_request set delivery_state='SUPERSEDED' "
                + "where workspace=? and ticket_id=? and delivery_state='PENDING'", workspace, id);
        jdbc.update("update servicedesk_enrichment_outbox set state='SUPERSEDED',claim_id=null,lease_until=null "
                        + "where workspace=? and ticket_id=? and event_type='EVALUATE_CONTEXT' "
                        + "and state in ('PENDING','PROCESSING') and (context_revision<>? or policy_digest<>?)",
                workspace, id, revision, policy.digest());
        outbox(workspace, id, revision, "EVALUATE_CONTEXT", now.plusMillis(policy.debounceMillis()));
        transition(workspace, id, revision, late ? "LATE_ANSWER_REVIEW_REQUIRED" : "CONTEXT_REVISION_CHANGED", false);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void invalidateExisting(String workspace, UUID id, long revision) {
        int changed = jdbc.update("update servicedesk_enrichment_case set context_revision=?,context_digest=null,"
                        + "readiness_json=null,status='MANUAL_REVIEW',manual_required=true,"
                        + "reason_codes='[\"ENRICHMENT_DISABLED\"]'::jsonb,version=version+1,updated_at=? "
                        + "where workspace=? and ticket_id=? and context_revision<>?",
                revision, time(clock.instant()), workspace, id, revision);
        if (changed > 0) {
            jdbc.update("update servicedesk_enrichment_question_request set delivery_state='SUPERSEDED' "
                    + "where workspace=? and ticket_id=?", workspace, id);
            jdbc.update("update servicedesk_enrichment_outbox set state='SUPERSEDED',claim_id=null,lease_until=null "
                            + "where workspace=? and ticket_id=? and event_type='EVALUATE_CONTEXT' and state in ('PENDING','PROCESSING')",
                    workspace, id);
        }
    }

    public Optional<EnrichmentCase> find(String workspace, UUID id) {
        return jdbc.query("select * from servicedesk_enrichment_case where workspace=? and ticket_id=?",
                this::map, workspace, id).stream().findFirst();
    }

    public List<EnrichmentCase> pending(String workspace, int page) {
        return jdbc.query("select * from servicedesk_enrichment_case where workspace=? "
                        + "and (manual_required or status not in ('READY','CANCELLED_BY_PROCESS')) "
                        + "order by manual_required desc,updated_at,ticket_id limit 50 offset ?", this::map, workspace, page * 50);
    }

    @Transactional
    public Optional<Claim> claim(String workspace, UUID id) {
        long revision = lockTicket(workspace, id, false).orElseThrow();
        var state = findLocked(workspace, id).orElseThrow(() -> CapabilityException.notFound("Enrichment case not found"));
        Instant now = clock.instant();
        if (!state.lateAnswer() && state.deadlineAt() != null && !state.deadlineAt().isAfter(now)) {
            manual(workspace, id, "CLARIFICATION_TIMEOUT", true);
            return Optional.empty();
        }
        var rows = jdbc.query("select id,attempts from servicedesk_enrichment_outbox where workspace=? "
                        + "and ticket_id=? and event_type='EVALUATE_CONTEXT' and context_revision=? and policy_digest=? "
                        + "and available_at<=? and (state='PENDING' or (state='PROCESSING' and lease_until<=?)) "
                        + "order by available_at,id limit 1 for update skip locked",
                (row, number) -> new Object[] {row.getObject("id", UUID.class), row.getInt("attempts")},
                workspace, id, revision, policy.digest(), time(now), time(now));
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        UUID eventId = (UUID) rows.get(0)[0];
        if ((int) rows.get(0)[1] >= 2) {
            jdbc.update("update servicedesk_enrichment_outbox set state='DEAD',last_error_code='ATTEMPT_LIMIT' where id=?", eventId);
            manual(workspace, id, "ATTEMPT_LIMIT", false);
            return Optional.empty();
        }
        UUID claimId = UUID.randomUUID();
        jdbc.update("update servicedesk_enrichment_outbox set state='PROCESSING',claim_id=?,lease_until=?,attempts=attempts+1 where id=?",
                claimId, time(now.plusSeconds(60)), eventId);
        jdbc.update("update servicedesk_enrichment_case set status='EVALUATING',version=version+1,updated_at=? "
                + "where workspace=? and ticket_id=?", time(now), workspace, id);
        return Optional.of(new Claim(eventId, claimId, revision, state.version() + 1, state));
    }

    @Transactional
    public boolean complete(String workspace, UUID id, Claim claim, ContextReadinessView readiness, boolean processOpen) {
        long revision = lockTicket(workspace, id, false).orElseThrow();
        var state = findLocked(workspace, id).orElseThrow();
        if (revision != claim.revision() || state.version() != claim.caseVersion()
                || readiness.contextRevision() != revision || !policy.digest().equals(readiness.policyDigest())
                || !owns(workspace, id, claim)) {
            return false;
        }
        Instant now = clock.instant();
        var reasons = new ArrayList<>(readiness.reasonCodes());
        boolean manual = state.lateAnswer() || readiness.overall() == ContextReadinessView.State.MANUAL_REVIEW_REQUIRED;
        if (state.lateAnswer()) {
            reasons.add("LATE_ANSWER_REVIEW_REQUIRED");
        }
        String status = manual ? "MANUAL_REVIEW" : switch (readiness.overall()) {
            case READINESS_OK -> "READY";
            case ENRICHMENT_OPTIONAL -> "OPTIONAL_ENRICHMENT";
            case ENRICHMENT_REQUIRED -> "AWAITING_ANSWER";
            default -> "MANUAL_REVIEW";
        };
        if (!processOpen) {
            status = "CANCELLED_BY_PROCESS";
            reasons.add("PROCESS_ALREADY_CLOSED");
            manual |= revision > 0;
        }
        int rounds = state.roundCount();
        Instant deadline = null, reminder = null;
        if (processOpen && !manual && !readiness.questions().isEmpty()) {
            if (rounds >= policy.maxRounds()) {
                status = "MANUAL_REVIEW";
                manual = true;
                reasons.add("MAX_CLARIFICATION_ROUNDS");
            } else {
                rounds++;
                deadline = now.plusSeconds(reasons.contains("HIGH_URGENCY_REVIEW") ? policy.highWaitSeconds() : policy.waitSeconds());
                reminder = now.plusSeconds(Math.min(policy.reminderSeconds(), Math.max(1, (deadline.getEpochSecond() - now.getEpochSecond()) / 2)));
                jdbc.update("insert into servicedesk_enrichment_question_request "
                                + "(id,workspace,ticket_id,context_revision,round_count,policy_digest,questions_json,created_at) "
                                + "values (?,?,?,?,?,?,cast(? as jsonb),?) on conflict (ticket_id,round_count) do nothing",
                        UUID.randomUUID(), workspace, id, revision, rounds, policy.digest(), encode(readiness.questions()), time(now));
                outbox(workspace, id, revision, "QUESTION_REQUESTED", now);
            }
        }
        manual |= reasons.contains("HIGH_URGENCY_REVIEW");
        jdbc.update("update servicedesk_enrichment_case set context_digest=?,status=?,round_count=?,manual_required=?,"
                        + "deadline_at=?,reminder_due_at=?,reminded_at=null,readiness_json=cast(? as jsonb),"
                        + "reason_codes=cast(? as jsonb),last_evaluated_at=?,updated_at=?,version=version+1 "
                        + "where workspace=? and ticket_id=?",
                readiness.contextDigest(), status, rounds, manual, time(deadline), time(reminder), encode(readiness),
                encode(reasons), time(now), time(now), workspace, id);
        jdbc.update("update servicedesk_enrichment_outbox set state='DONE',lease_until=null where id=? and claim_id=?", claim.eventId(), claim.claimId());
        if (manual) {
            outbox(workspace, id, revision, "MANUAL_REVIEW_REQUIRED", now);
        }
        audit.record(workspace, new vn.haui.dx.hpdi.human.api.BusinessActivity(
                "servicedesk.enrichment.evaluate", "servicedesk.ticket", id.toString(),
                java.util.Map.of("contextVersion", Long.toString(revision), "caseVersion", Long.toString(state.version() + 1),
                        "eventId", claim.eventId().toString())));
        transition(workspace, id, revision, status, false);
        return true;
    }

    @Transactional
    public void fail(String workspace, UUID id, Claim claim, String reason) {
        lockTicket(workspace, id, false);
        var state = findLocked(workspace, id);
        if (state.isPresent() && state.get().version() == claim.caseVersion() && owns(workspace, id, claim)) {
            jdbc.update("update servicedesk_enrichment_outbox set state='DEAD',last_error_code=?,lease_until=null "
                    + "where id=? and claim_id=?", reason, claim.eventId(), claim.claimId());
            manual(workspace, id, reason, false);
        }
    }

    @Transactional
    public int expireDue() {
        Instant now = clock.instant();
        var due = jdbc.query("select workspace,ticket_id from servicedesk_enrichment_case "
                        + "where status in ('NEW','REASSESS_QUEUED','EVALUATING','AWAITING_ANSWER','OPTIONAL_ENRICHMENT') "
                        + "and (deadline_at<=? or (reminder_due_at<=? and reminded_at is null)) "
                        + "order by deadline_at,ticket_id limit 50",
                (row, number) -> new Object[] {row.getString("workspace"), row.getObject("ticket_id", UUID.class)}, time(now), time(now));
        int changed = 0;
        for (var row : due) {
            String workspace = (String) row[0];
            UUID id = (UUID) row[1];
            if (lockTicket(workspace, id, true).isEmpty()) {
                continue;
            }
            var state = findLocked(workspace, id).orElseThrow();
            if (!List.of("NEW", "REASSESS_QUEUED", "EVALUATING", "AWAITING_ANSWER", "OPTIONAL_ENRICHMENT").contains(state.status())) {
                continue;
            }
            if (state.deadlineAt() != null && !state.deadlineAt().isAfter(now)) {
                manual(workspace, id, "CLARIFICATION_TIMEOUT", true);
                changed++;
            } else if (state.reminderDueAt() != null && !state.reminderDueAt().isAfter(now) && state.remindedAt() == null) {
                outbox(workspace, id, state.contextRevision(), "REMINDER_DUE", now);
                jdbc.update("update servicedesk_enrichment_case set reminded_at=?,version=version+1,updated_at=? "
                        + "where workspace=? and ticket_id=?", time(now), time(now), workspace, id);
                transition(workspace, id, state.contextRevision(), "REMINDER_DUE", true);
                changed++;
            }
        }
        return changed;
    }

    public Optional<UUID> currentRequest(String workspace, UUID id, long revision) {
        return jdbc.query("select id from servicedesk_enrichment_question_request "
                        + "where workspace=? and ticket_id=? and context_revision=? and delivery_state='PENDING' "
                        + "order by round_count desc limit 1", (row, number) -> row.getObject("id", UUID.class), workspace, id, revision)
                .stream().findFirst();
    }

    private boolean owns(String workspace, UUID id, Claim claim) {
        return jdbc.queryForObject("select count(*) from servicedesk_enrichment_outbox where workspace=? and ticket_id=? "
                        + "and id=? and claim_id=? and state='PROCESSING' and lease_until>?", Long.class,
                workspace, id, claim.eventId(), claim.claimId(), time(clock.instant())) == 1;
    }

    private Optional<Long> lockTicket(String workspace, UUID id, boolean skipLocked) {
        var rows = jdbc.query("select context_revision from servicedesk_ticket where workspace=? and id=? for update"
                        + (skipLocked ? " skip locked" : ""), (row, number) -> row.getLong(1), workspace, id);
        return rows.stream().findFirst();
    }

    private Optional<EnrichmentCase> findLocked(String workspace, UUID id) {
        return jdbc.query("select * from servicedesk_enrichment_case where workspace=? and ticket_id=? for update",
                this::map, workspace, id).stream().findFirst();
    }

    private void manual(String workspace, UUID id, String reason, boolean timer) {
        var state = findLocked(workspace, id).orElseThrow();
        jdbc.update("update servicedesk_enrichment_case set status=?,manual_required=true,reason_codes=cast(? as jsonb),"
                        + "version=version+1,updated_at=? where workspace=? and ticket_id=?",
                "CLARIFICATION_TIMEOUT".equals(reason) ? "TIMED_OUT" : "MANUAL_REVIEW", encode(List.of(reason)),
                time(clock.instant()), workspace, id);
        jdbc.update("update servicedesk_enrichment_outbox set state='SUPERSEDED',lease_until=null,claim_id=null "
                        + "where workspace=? and ticket_id=? and event_type='EVALUATE_CONTEXT' and state in ('PENDING','PROCESSING')",
                workspace, id);
        outbox(workspace, id, state.contextRevision(), "MANUAL_REVIEW_REQUIRED", clock.instant());
        transition(workspace, id, state.contextRevision(), reason, timer);
    }

    private void outbox(String workspace, UUID id, long revision, String type, Instant available) {
        jdbc.update("insert into servicedesk_enrichment_outbox "
                        + "(id,workspace,ticket_id,context_revision,event_type,policy_digest,available_at) "
                        + "values (?,?,?,?,?,?,?) on conflict (ticket_id,context_revision,event_type,policy_digest) do nothing",
                UUID.randomUUID(), workspace, id, revision, type, policy.digest(), time(available));
    }

    private void transition(String workspace, UUID id, long revision, String reason, boolean timer) {
        jdbc.update("insert into servicedesk_enrichment_transition "
                        + "(id,workspace,ticket_id,context_revision,case_version,reason_code,origin,occurred_at) "
                        + "select ?,workspace,ticket_id,?,version,?,?,? from servicedesk_enrichment_case "
                        + "where workspace=? and ticket_id=? on conflict (ticket_id,case_version,reason_code) do nothing",
                UUID.randomUUID(), revision, reason, timer ? "DURABLE_TIMER" : "AUTHENTICATED_COMMAND", time(clock.instant()), workspace, id);
    }

    private EnrichmentCase map(ResultSet row, int number) throws SQLException {
        try {
            String readiness = row.getString("readiness_json");
            return new EnrichmentCase(row.getObject("ticket_id", UUID.class), row.getString("workspace"),
                    row.getLong("context_revision"), row.getString("context_digest"), row.getString("policy_digest"),
                    row.getString("status"), row.getInt("round_count"), row.getBoolean("manual_required"), row.getBoolean("late_answer"),
                    instant(row, "deadline_at"), instant(row, "reminder_due_at"), instant(row, "reminded_at"), row.getLong("version"),
                    json.readValue(row.getString("reason_codes"), new TypeReference<List<String>>() {}),
                    readiness == null ? null : json.readValue(readiness, ContextReadinessView.class));
        } catch (java.io.IOException failure) {
            throw new SQLException("Invalid enrichment metadata", failure);
        }
    }

    private String encode(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (java.io.IOException failure) {
            throw new IllegalStateException("Cannot encode enrichment metadata", failure);
        }
    }

    private static Instant instant(ResultSet row, String column) throws SQLException {
        Timestamp value = row.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    private static Timestamp time(Instant value) { return value == null ? null : Timestamp.from(value); }
}
