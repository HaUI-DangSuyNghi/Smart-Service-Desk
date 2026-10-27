package vn.haui.dx.apps.servicedesk.clarification;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Persistence riêng của Service Desk; chỉ đọc/ghi bảng do ứng dụng sở hữu. */
@Repository
public class TicketContextStore {
    public record TicketState(UUID id, String title, String description, long revision) {}

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public TicketContextStore(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    public Optional<TicketState> state(String workspace, UUID ticketId, boolean lock) {
        return jdbc.query(
                        "select id,title,description,context_revision from servicedesk_ticket "
                                + "where workspace=? and id=?" + (lock ? " for update" : ""),
                        (row, number) -> new TicketState(
                                row.getObject("id", UUID.class), row.getString("title"),
                                row.getString("description"), row.getLong("context_revision")),
                        workspace, ticketId)
                .stream().findFirst();
    }

    public List<ClarificationEvent> events(String workspace, UUID ticketId) {
        return jdbc.query(
                "select * from servicedesk_ticket_clarification where workspace=? and ticket_id=? "
                        + "order by new_revision", this::event, workspace, ticketId);
    }

    public Map<UUID, List<ClarificationEvent>> eventsBatch(String workspace, List<UUID> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        if (ids.size() > 50) {
            throw new IllegalArgumentException("Candidate budget exceeded");
        }
        var parameters = new java.util.ArrayList<Object>();
        parameters.add(workspace);
        parameters.addAll(ids);
        String placeholders = String.join(",", java.util.Collections.nCopies(ids.size(), "?"));
        var events = jdbc.query(
                "select * from servicedesk_ticket_clarification where workspace=? and ticket_id in ("
                        + placeholders + ") order by ticket_id,new_revision", this::event, parameters.toArray());
        return events.stream().collect(java.util.stream.Collectors.groupingBy(ClarificationEvent::ticketId));
    }

    public Optional<ClarificationEvent> replay(
            String workspace, UUID ticketId, String actor, UUID clientAnswerId) {
        return jdbc.query(
                        "select * from servicedesk_ticket_clarification "
                                + "where workspace=? and ticket_id=? and actor=? and client_answer_id=?",
                        this::event, workspace, ticketId, actor, clientAnswerId)
                .stream().findFirst();
    }

    public ClarificationEvent append(
            String workspace, UUID ticketId, String actor, ClarificationRequest request,
            String answersJson, String payloadDigest) {
        long expected = request.expectedContextRevision();
        int changed = jdbc.update(
                "update servicedesk_ticket set context_revision=context_revision+1 "
                        + "where workspace=? and id=? and context_revision=?",
                workspace, ticketId, expected);
        if (changed != 1) {
            throw vn.haui.dx.hpdi.common.api.CapabilityException.conflict("Context revision changed");
        }

        return jdbc.queryForObject(
                "insert into servicedesk_ticket_clarification "
                        + "(id,workspace,ticket_id,actor,client_answer_id,expected_revision,new_revision,"
                        + "answers_json,payload_digest) values (?,?,?,?,?,?,?,cast(? as jsonb),?) returning *",
                this::event, UUID.randomUUID(), workspace, ticketId, actor, request.clientAnswerId(),
                expected, expected + 1, answersJson, payloadDigest);
    }

    private ClarificationEvent event(ResultSet row, int number) throws SQLException {
        try {
            List<ClarificationRequest.Answer> answers = json.readValue(
                    row.getString("answers_json"), new TypeReference<>() {});
            return new ClarificationEvent(
                    row.getObject("id", UUID.class), row.getObject("ticket_id", UUID.class),
                    row.getString("actor"), row.getObject("client_answer_id", UUID.class),
                    row.getLong("expected_revision"), row.getLong("new_revision"), answers,
                    row.getString("payload_digest"), row.getTimestamp("created_at").toInstant());
        } catch (java.io.IOException failure) {
            throw new SQLException("Invalid persisted clarification", failure);
        }
    }
}
