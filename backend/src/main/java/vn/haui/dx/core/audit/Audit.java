package vn.haui.dx.core.audit;

import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class Audit {
    private final JdbcTemplate jdbc;
    public Audit(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    public void record(String actor, String action, String resource, String workspace, String requestId) {
        jdbc.update("insert into audit_event(id,actor,action,resource,workspace,request_id) values (?,?,?,?,?,?)",
            UUID.randomUUID(), actor, action, resource, workspace, requestId);
    }
}
