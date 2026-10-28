package vn.haui.dx.core.audit.internal;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import vn.haui.dx.core.audit.api.AuditEntry;
import vn.haui.dx.core.audit.api.AuditQuery;
import vn.haui.dx.core.audit.api.AuditService;
import vn.haui.dx.core.common.CoreException;
import vn.haui.dx.core.common.Page;
import vn.haui.dx.core.common.PageRequest;
import vn.haui.dx.core.common.RequestContext;
import vn.haui.dx.core.identity.api.CurrentUser;
import vn.haui.dx.core.identity.api.WorkspaceAccess;
import vn.haui.dx.core.identity.api.FreshAdminAuthorization;
import static vn.haui.dx.core.identity.api.WorkspaceRole.COORDINATOR;

@Service
class JdbcAuditService implements AuditService {
    /** Dạng {@code <đối tượng>.<động từ>}, cho phép tiền tố app: {@code servicedesk.ticket.create}. */
    static final Pattern ACTION = Pattern.compile("[a-z0-9-]+(\\.[a-z0-9-]+)+");
    private static final TypeReference<Map<String, Object>> DETAILS = new TypeReference<>() {};

    private final JdbcTemplate jdbc;
    private final WorkspaceAccess access;
    private final ObjectMapper json;
    private final FreshAdminAuthorization admin;

    JdbcAuditService(JdbcTemplate jdbc, WorkspaceAccess access, ObjectMapper json, FreshAdminAuthorization admin) {
        this.jdbc = jdbc; this.access = access; this.json = json; this.admin = admin;
    }

    // MANDATORY: audit phải commit/rollback cùng thao tác nghiệp vụ, không được ghi lẻ
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void record(String action, String resourceType, String resourceId, String workspace, Map<String, Object> details) {
        recordAs(access.current().sub(), action, resourceType, resourceId, workspace, details);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void recordSystem(String action, String resourceType, String resourceId, String workspace, Map<String, Object> details) {
        recordAs("system:workflow", action, resourceType, resourceId, workspace, details);
    }

    private void recordAs(String actor, String action, String resourceType, String resourceId, String workspace, Map<String, Object> details) {
        if (action == null || action.length() > 100 || !ACTION.matcher(action).matches())
            throw new IllegalArgumentException("Invalid audit action: " + action);
        require(resourceType, 100, "resourceType");
        require(resourceId, 200, "resourceId");
        require(workspace, 100, "workspace");
        // Ngoài HTTP request (job nền) thì tự sinh request ID
        String requestId = RequestContext.requestId().orElseGet(() -> UUID.randomUUID().toString());
        jdbc.update("insert into audit_event(id,actor,action,resource_type,resource,workspace,request_id,details) "
                + "values (?,?,?,?,?,?,?,cast(? as jsonb))",
            UUID.randomUUID(), actor, action, resourceType, resourceId, workspace, requestId, write(details));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<AuditEntry> search(String workspace, AuditQuery query, PageRequest page) {
        if (workspace != null && workspace.startsWith("system:")) {
            admin.requirePlatformAdmin();
            return find(workspace, query, page);
        }
        CurrentUser user = access.current();
        if (!user.platformAdmin() && !user.hasRole(workspace, COORDINATOR))
            throw CoreException.forbidden("Audit read requires coordinator or platform-admin");
        return find(workspace, query, page);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<AuditEntry> mine(String workspace, AuditQuery query, PageRequest page) {
        if (workspace != null && workspace.startsWith("system:"))
            throw CoreException.forbidden("Reserved audit scope is not a workspace");
        access.requireMember(workspace);
        var own = new AuditQuery(query.from(), query.to(), query.action(), access.current().sub());
        return find(workspace, own, page);
    }

    private Page<AuditEntry> find(String workspace, AuditQuery query, PageRequest page) {
        StringBuilder where = new StringBuilder(" where workspace = ?");
        List<Object> args = new ArrayList<>(List.of(workspace));
        if (query.from() != null) { where.append(" and occurred_at >= ?"); args.add(Timestamp.from(query.from())); }
        if (query.to() != null) { where.append(" and occurred_at < ?"); args.add(Timestamp.from(query.to())); }
        if (query.action() != null) { where.append(" and action = ?"); args.add(query.action()); }
        if (query.actor() != null) { where.append(" and actor = ?"); args.add(query.actor()); }
        Long total = jdbc.queryForObject("select count(*) from audit_event" + where, Long.class, args.toArray());
        args.add(page.size());
        args.add(page.offset());
        List<AuditEntry> items = jdbc.query(
            "select id,occurred_at,actor,action,resource_type,resource,workspace,request_id,details::text as details from audit_event"
                + where + " order by occurred_at desc, id desc limit ? offset ?", this::map, args.toArray());
        return new Page<>(items, page.page(), page.size(), total == null ? 0 : total);
    }

    private AuditEntry map(ResultSet row, int index) throws SQLException {
        return new AuditEntry(row.getObject("id", UUID.class), row.getTimestamp("occurred_at").toInstant(), row.getString("actor"),
            row.getString("action"), row.getString("resource_type"), row.getString("resource"), row.getString("workspace"),
            row.getString("request_id"), read(row.getString("details")));
    }

    private static void require(String value, int max, String name) {
        if (value == null || value.isBlank() || value.length() > max) throw new IllegalArgumentException("Invalid audit " + name);
    }

    private String write(Map<String, Object> details) {
        try {
            return json.writeValueAsString(details == null ? Map.of() : details);
        } catch (JsonProcessingException error) {
            throw new IllegalArgumentException("Audit details must be JSON-serializable", error);
        }
    }

    private Map<String, Object> read(String text) {
        try {
            return json.readValue(text, DETAILS);
        } catch (JsonProcessingException error) {
            throw new IllegalStateException("Corrupt audit details", error);
        }
    }
}
