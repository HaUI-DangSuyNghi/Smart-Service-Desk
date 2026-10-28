package vn.haui.dx.apps.servicedesk;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import vn.haui.dx.hpdi.common.api.CapabilityException;
import vn.haui.dx.hpdi.settings.api.SettingsAdministration;
import vn.haui.dx.hpdi.settings.api.SettingsValidation;
import static vn.haui.dx.hpdi.settings.api.SettingsValidation.*;

@RestController
@RequestMapping("/api/apps/servicedesk/v1/settings")
public class ServiceDeskSettings {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final SettingsAdministration admin;
    public ServiceDeskSettings(JdbcTemplate jdbc, ObjectMapper json, SettingsAdministration admin) {
        this.jdbc = jdbc; this.json = json; this.admin = admin;
    }
    @GetMapping
    @Transactional(readOnly = true)
    public ObjectNode get() {
        admin.requireAdmin();
        return jdbc.queryForObject("select * from servicedesk_settings where id=1", this::read);
    }
    @PutMapping(consumes = "application/json")
    @Transactional
    public ObjectNode replace(@RequestBody String body) {
        String actor = admin.requireAdmin();
        ObjectNode request = SettingsValidation.parse(json, body);
        validate(request);
        long version = request.get("version").longValue();
        ObjectNode before = jdbc.queryForObject("select * from servicedesk_settings where id=1 for update", this::read);
        request.remove("version");
        var rows = jdbc.query("update servicedesk_settings set payload=cast(? as jsonb),version=version+1,"
            + "updated_at=current_timestamp,updated_by=? where id=1 and version=? and version<9223372036854775807 returning *",
            this::read, request.toString(), actor, version);
        if (rows.isEmpty()) throw CapabilityException.conflict("Settings version stale or exhausted");
        ObjectNode after = rows.get(0);
        admin.audit("servicedesk.settings.update", before, after);
        return after;
    }
    static void validate(JsonNode root) {
        object(root, "version", "categoryGroups", "estimateDefaults");
        integer(root.get("version"), 0, Long.MAX_VALUE);
        object(root.get("categoryGroups"), "HARDWARE", "NETWORK", "SOFTWARE", "ACCOUNT", "OTHER");
        object(root.get("estimateDefaults"), "HARDWARE", "NETWORK", "SOFTWARE", "ACCOUNT", "OTHER");
        require(root.get("categoryGroups").get("OTHER").isNull(), "OTHER requires Coordinator selection and must remain null");
        for (String category : Set.of("HARDWARE", "NETWORK", "SOFTWARE", "ACCOUNT")) {
            String group = text(root.get("categoryGroups").get(category), 50);
            require(group.matches("[a-z][a-z0-9-]{0,49}") && !Set.of("requester", "agent", "coordinator", "platform-admin", "system", "global").contains(group),
                "Safe specialty group name required");
        }
        for (var row : root.get("estimateDefaults")) {
            object(row, "LOW", "MEDIUM", "HIGH", "CRITICAL");
            for (var minutes : row) integer(minutes, 1, 10080);
        }
    }
    private ObjectNode read(ResultSet row, int index) throws SQLException {
        ObjectNode result = SettingsValidation.parse(json, row.getString("payload"));
        result.put("version", row.getLong("version"));
        result.put("updatedAt", row.getTimestamp("updated_at").toInstant().toString());
        result.put("updatedBy", row.getString("updated_by"));
        return result;
    }
}
