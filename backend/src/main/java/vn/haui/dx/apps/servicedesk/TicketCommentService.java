package vn.haui.dx.apps.servicedesk;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.haui.dx.hpdi.common.api.CapabilityException;
import vn.haui.dx.hpdi.human.api.ActivityJournal;
import vn.haui.dx.hpdi.human.api.BusinessActivity;
import vn.haui.dx.hpdi.human.api.HumanContext;

/** Bình luận trên ticket: quyền đọc/viết theo quyền xem ticket (người gửi chỉ ticket của mình). */
@Service
public class TicketCommentService {
    public record Comment(UUID id, String author, String body, Instant createdAt) {}
    public record NewComment(String body) {}

    private final JdbcTemplate jdbc;
    private final TicketService tickets;
    private final HumanContext humans;
    private final ActivityJournal audit;
    private final ObjectProvider<TicketNotices> notices;
    private final ObjectProvider<TicketRealtime> realtime;

    public TicketCommentService(JdbcTemplate jdbc, TicketService tickets, HumanContext humans, ActivityJournal audit,
                                ObjectProvider<TicketNotices> notices, ObjectProvider<TicketRealtime> realtime) {
        this.jdbc = jdbc; this.tickets = tickets; this.humans = humans; this.audit = audit; this.notices = notices;
        this.realtime = realtime;
    }

    @Transactional(readOnly = true)
    public List<Comment> list(String workspace, UUID ticketId) {
        tickets.get(workspace, ticketId);
        return jdbc.query("select id,author,body,created_at from servicedesk_ticket_comment where workspace=? and ticket_id=?"
                + " order by created_at, id", (row, index) -> new Comment(row.getObject("id", UUID.class), row.getString("author"),
                row.getString("body"), row.getTimestamp("created_at").toInstant()), workspace, ticketId);
    }

    @Transactional
    public Comment add(String workspace, UUID ticketId, NewComment input) {
        var ticket = tickets.get(workspace, ticketId);
        String body = input == null || input.body() == null ? null : input.body().strip();
        if (body == null || body.isEmpty() || body.length() > 2000 || body.codePoints().anyMatch(code ->
            Character.isISOControl(code) && code != '\n' && code != '\r' && code != '\t'))
            throw CapabilityException.validation("Invalid comment");
        String author = humans.currentActor(workspace).subject();
        // Người nhận: người gửi, người xử lý gần nhất và những ai đã bình luận (người viết tự bị loại)
        var subjects = new HashSet<>(jdbc.queryForList("select distinct author from servicedesk_ticket_comment"
            + " where workspace=? and ticket_id=?", String.class, workspace, ticketId));
        var comment = new Comment(UUID.randomUUID(), author, body, null);
        Instant createdAt = jdbc.queryForObject("insert into servicedesk_ticket_comment(id,workspace,ticket_id,author,body)"
            + " values(?,?,?,?,?) returning created_at", (row, index) -> row.getTimestamp(1).toInstant(),
            comment.id(), workspace, ticketId, author, body);
        audit.record(workspace, new BusinessActivity("servicedesk.ticket.comment", "servicedesk.ticket", ticketId.toString(),
            Map.of("commentId", comment.id().toString())));
        subjects.add(ticket.creator());
        var handler = notices.getObject().lastAssignee(workspace, ticket.process().processInstanceId());
        if (handler != null) subjects.add(handler);
        notices.getObject().commented(workspace, ticketId, body, subjects);
        realtime.ifAvailable(live -> live.commented(workspace, ticketId));
        return new Comment(comment.id(), author, body, createdAt);
    }
}
