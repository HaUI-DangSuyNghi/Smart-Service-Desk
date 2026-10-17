package vn.haui.dx.apps.servicedesk;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import vn.haui.dx.core.common.Page;
import vn.haui.dx.core.common.PageRequest;
import vn.haui.dx.core.workflow.api.ProcessRef;

@Repository
class TicketRepository {
    private final JdbcTemplate jdbc;

    TicketRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    Ticket insert(UUID id, String workspace, String creator, NewTicket input, String processId) {
        return jdbc.queryForObject("insert into servicedesk_ticket "
                + "(id,workspace,creator,client_request_id,title,description,process_instance_id) "
                + "values (?,?,?,?,?,?,?) returning *", TicketRepository::map,
            id, workspace, creator, input.clientRequestId(), input.title(), input.description(), processId);
    }

    Optional<Ticket> find(String workspace, UUID id, String creator) {
        return jdbc.query("select * from servicedesk_ticket where workspace=? and id=? "
                + "and (cast(? as varchar) is null or creator=?)", TicketRepository::map,
            workspace, id, creator, creator).stream().findFirst();
    }

    Page<Ticket> search(String workspace, String creator, String query, PageRequest page) {
        String where = " where workspace=?";
        List<Object> parameters = new ArrayList<>(List.of(workspace));
        if (creator != null) {
            where += " and creator=?";
            parameters.add(creator);
        }
        if (query != null && !query.isEmpty()) {
            where += " and position(lower(?) in lower(title || ' ' || description)) > 0";
            parameters.add(query);
        }
        Long total = jdbc.queryForObject("select count(*) from servicedesk_ticket" + where,
            Long.class, parameters.toArray());
        parameters.add(page.size());
        parameters.add(page.offset());
        var items = jdbc.query("select * from servicedesk_ticket" + where
            + " order by created_at desc,id desc limit ? offset ?", TicketRepository::map, parameters.toArray());
        return new Page<>(items, page.page(), page.size(), total == null ? 0 : total);
    }

    private static Ticket map(ResultSet row, int rowNumber) throws SQLException {
        UUID id = row.getObject("id", UUID.class);
        String workspace = row.getString("workspace");
        return new Ticket(id, workspace, row.getString("creator"), row.getObject("client_request_id", UUID.class),
            row.getString("title"), row.getString("description"), row.getTimestamp("created_at").toInstant(),
            new ProcessRef(row.getString("process_instance_id"), ServiceDeskProcess.KEY, id.toString(), workspace));
    }
}
