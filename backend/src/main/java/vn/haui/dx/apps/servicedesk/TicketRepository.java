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

    TicketStatistics statistics(String workspace, String creator, String query, Instant from, Instant to) {
        List<Object> parameters = new ArrayList<>(List.of(workspace));
        String where = where(creator, query, from, to, parameters);
        String sql = """
            with states as (
                select case
                    when s.id is null or h.proc_inst_id_ is null then 'unclassified'
                    when h.end_time_ is not null and h.delete_reason_ is null
                        and h.end_act_id_='end' and tasks.total=0 then 'completed'
                    when h.end_time_ is null and tasks.total=1 and tasks.handling=1 then 'handling'
                    when h.end_time_ is null and tasks.total=1 and tasks.confirming=1 then 'confirming'
                    else 'unclassified' end as state
                from servicedesk_ticket t
                left join process_scope s on s.id=t.process_instance_id and s.workspace=t.workspace
                    and s.owner=t.creator and s.business_key=cast(t.id as varchar) and s.process_key='service-desk'
                left join act_hi_procinst h on h.proc_inst_id_=s.id
                left join lateral (
                    select count(*) as total,
                        count(*) filter (where task_def_key_='handle' and suspension_state_=1) as handling,
                        count(*) filter (where task_def_key_='confirm' and suspension_state_=1) as confirming
                    from act_ru_task where proc_inst_id_=s.id
                ) tasks on true
            """ + where + """
            ) select count(*) as total,
                count(*) filter (where state='handling') as handling,
                count(*) filter (where state='confirming') as confirming,
                count(*) filter (where state='completed') as completed,
                count(*) filter (where state='unclassified') as unclassified from states
            """;
        return jdbc.queryForObject(sql, (row, rowNumber) -> new TicketStatistics(workspace,
            creator == null ? "workspace" : "own", row.getLong("total"), row.getLong("handling"),
            row.getLong("confirming"), row.getLong("completed"), row.getLong("unclassified")), parameters.toArray());
    }

    private static Ticket map(ResultSet row, int rowNumber) throws SQLException {
        UUID id = row.getObject("id", UUID.class);
        String workspace = row.getString("workspace");
        return new Ticket(id, workspace, row.getString("creator"), row.getObject("client_request_id", UUID.class),
            row.getString("title"), row.getString("description"), row.getTimestamp("created_at").toInstant(),
            new ProcessRef(row.getString("process_instance_id"), ServiceDeskProcess.KEY, id.toString(), workspace));
    }
}
