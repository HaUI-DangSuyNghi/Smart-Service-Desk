package vn.haui.dx.apps.servicedesk;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import vn.haui.dx.hpdi.common.api.Page;
import vn.haui.dx.hpdi.common.api.PageRequest;

@Repository
class TicketRepository {
    private final JdbcTemplate jdbc;

    TicketRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    boolean hasIntake(String workspace, UUID id) {
        return Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from servicedesk_intake_admission "
                + "where workspace=? and ticket_id=?)", Boolean.class, workspace, id));
    }

    Ticket insert(UUID id, String workspace, String creator, NewTicket input, String processId) {
        return insert(id, workspace, creator, input, processId, ServiceDeskProcess.REVIEWED_KEY);
    }

    Ticket insert(UUID id, String workspace, String creator, NewTicket input, String processId, String processKey) {
        return jdbc.queryForObject("insert into servicedesk_ticket "
                + "(id,workspace,creator,client_request_id,title,description,process_instance_id,process_key,awaiting_review) "
                + "values (?,?,?,?,?,?,?,?,?) returning *", TicketRepository::map,
            id, workspace, creator, input.clientRequestId(), input.title(), input.description(), processId, processKey,
            !ServiceDeskProcess.INTAKE_KEY.equals(processKey));
    }

    Optional<Ticket> find(String workspace, UUID id, String creator) {
        return jdbc.query("select * from servicedesk_ticket where workspace=? and id=? "
                + "and (cast(? as varchar) is null or creator=?)", TicketRepository::map,
            workspace, id, creator, creator).stream().findFirst();
    }

    private static String where(String creator, String query, Instant from, Instant to, List<Object> parameters) {
        String where = " where t.workspace=?";
        if (creator != null) {
            where += " and t.creator=?";
            parameters.add(creator);
        }
        if (query != null && !query.isEmpty()) {
            where += " and position(lower(?) in lower(t.title || ' ' || t.description)) > 0";
            parameters.add(query);
        }
        if (from != null) {
            where += " and t.created_at>=?";
            parameters.add(Timestamp.from(from));
        }
        if (to != null) {
            where += " and t.created_at<?";
            parameters.add(Timestamp.from(to));
        }
        return where;
    }

    Page<Ticket> search(String workspace, String creator, String query, Instant from, Instant to, PageRequest page) {
        List<Object> parameters = new ArrayList<>(List.of(workspace));
        String where = where(creator, query, from, to, parameters);
        Long total = jdbc.queryForObject("select count(*) from servicedesk_ticket t" + where,
            Long.class, parameters.toArray());
        parameters.add(page.size());
        parameters.add(page.offset());
        var items = jdbc.query("select t.* from servicedesk_ticket t" + where
            + " order by t.created_at desc,t.id desc limit ? offset ?", TicketRepository::map, parameters.toArray());
        return new Page<>(items, page.page(), page.size(), total == null ? 0 : total);
    }

    record ProcessLink(UUID id, String creator, String processId) {}

    Page<Ticket> awaitingReview(String workspace, String query, Instant from, Instant to, PageRequest page, boolean priority) {
        List<Object> parameters=new ArrayList<>(List.of(workspace));
        String where=where(null,query,from,to,parameters)+" and t.awaiting_review=true";
        long total=jdbc.queryForObject("select count(*) from servicedesk_ticket t"+where,Long.class,parameters.toArray());
        parameters.add(page.size()); parameters.add(page.offset());
        var items=jdbc.query("select t.* from servicedesk_ticket t left join servicedesk_review r on r.ticket_id=t.id"+where
            +(priority ? " order by case r.priority when 'CRITICAL' then 4 when 'HIGH' then 3 when 'MEDIUM' then 2 when 'LOW' then 1 else 0 end desc," : " order by")
            +" t.created_at asc,t.id asc limit ? offset ?",TicketRepository::map,parameters.toArray());
        return new Page<>(items,page.page(),page.size(),total);
    }

    List<ProcessLink> statisticsBatch(String workspace, String creator, String query, Instant from, Instant to,
                                      UUID after, int limit) {
        List<Object> parameters = new ArrayList<>(List.of(workspace));
        String where = where(creator, query, from, to, parameters);
        if (after != null) {
            where += " and t.id>?";
            parameters.add(after);
        }
        parameters.add(limit);
        return jdbc.query("select t.id,t.creator,t.process_instance_id from servicedesk_ticket t" + where
            + " order by t.id limit ?", (row, number) -> new ProcessLink(row.getObject("id", UUID.class),
                row.getString("creator"), row.getString("process_instance_id")), parameters.toArray());
    }

    private static Ticket map(ResultSet row, int rowNumber) throws SQLException {
        UUID id = row.getObject("id", UUID.class);
        String workspace = row.getString("workspace");
        return new Ticket(id, workspace, row.getString("creator"), row.getObject("client_request_id", UUID.class),
            row.getString("title"), row.getString("description"), row.getTimestamp("created_at").toInstant(),
            new TicketProcessView(row.getString("process_instance_id"), row.getString("process_key"), id.toString(), workspace));
    }
}
