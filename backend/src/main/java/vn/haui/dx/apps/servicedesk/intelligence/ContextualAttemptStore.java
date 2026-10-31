package vn.haui.dx.apps.servicedesk.intelligence;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public class ContextualAttemptStore {
    public record Key(String workspace, UUID ticketId, String inputDigest, String processingDigest, String policyDigest, String modelDigest) {}
    public record Observation(String state, String category) {}
    private final JdbcTemplate jdbc;

    public ContextualAttemptStore(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    /** INSERT admission được commit trước inference; không giữ transaction trong transport. */
    public Optional<UUID> admit(Key key) {
        UUID claim = UUID.randomUUID();
        return jdbc.query("insert into servicedesk_contextual_attempt "
                        + "(workspace,ticket_id,input_digest,processing_digest,policy_digest,model_digest,state,claim_id) "
                        + "values (?,?,?,?,?,?,'PROCESSING',?) on conflict do nothing returning claim_id",
                (row, number) -> row.getObject(1, UUID.class), key.workspace(), key.ticketId(), key.inputDigest(),
                key.processingDigest(), key.policyDigest(), key.modelDigest(), claim).stream().findFirst();
    }

    public Optional<Observation> find(Key key) {
        return jdbc.query("select state,category from servicedesk_contextual_attempt where workspace=? and ticket_id=? "
                        + "and input_digest=? and processing_digest=? and policy_digest=? and model_digest=?",
                (row, number) -> new Observation(row.getString("state"), row.getString("category")),
                key.workspace(), key.ticketId(), key.inputDigest(), key.processingDigest(), key.policyDigest(), key.modelDigest())
                .stream().findFirst();
    }

    public void finish(Key key, UUID claim, String state, String category) {
        jdbc.update("update servicedesk_contextual_attempt set state=?,category=?,completed_at=current_timestamp "
                        + "where workspace=? and ticket_id=? and input_digest=? and processing_digest=? "
                        + "and policy_digest=? and model_digest=? and claim_id=? and state='PROCESSING'",
                state, category, key.workspace(), key.ticketId(), key.inputDigest(), key.processingDigest(),
                key.policyDigest(), key.modelDigest(), claim);
    }
}
