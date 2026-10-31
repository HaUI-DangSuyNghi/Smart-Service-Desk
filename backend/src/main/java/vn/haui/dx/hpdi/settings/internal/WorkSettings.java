package vn.haui.dx.hpdi.settings.internal;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.sql.ResultSet;
import java.sql.SQLException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.haui.dx.hpdi.common.api.CapabilityException;
import vn.haui.dx.hpdi.settings.api.SettingsAdministration;
import vn.haui.dx.hpdi.settings.api.SettingsValidation;

@Service
public class WorkSettings {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final SettingsAdministration admin;
    private final WorkPolicyReader policy;
    public WorkSettings(JdbcTemplate jdbc, ObjectMapper json, SettingsAdministration admin, WorkPolicyReader policy) {
        this.jdbc = jdbc; this.json = json; this.admin = admin; this.policy = policy;
    }
    @Transactional(readOnly = true)
    public ObjectNode get() { admin.requireAdmin(); return current(); }
    @Transactional
    public ObjectNode replace(String body) {
        String actor = admin.requireAdmin();
        ObjectNode request = SettingsValidation.parse(json, body);
        WorkSettingsValidation.validate(request);
        long version = request.get("version").longValue();
        ObjectNode before = jdbc.queryForObject("select * from hpdi_work_settings where id=1 for update", this::read);
        request.remove("version");
        var rows = jdbc.query("update hpdi_work_settings set payload=cast(? as jsonb),version=version+1,"
            + "updated_at=current_timestamp,updated_by=? where id=1 and version=? and version<9223372036854775807 returning *",
            this::read, request.toString(), actor, version);
        if (rows.isEmpty()) throw CapabilityException.conflict("Settings version stale or exhausted");
        ObjectNode after = rows.get(0);
        admin.audit("hpdi.work-settings.update", before, after);
        // Áp dụng ngay cho thao tác sau (duyệt, bắt đầu, cảnh báo...); việc đã duyệt giữ hạn đã tính
        policy.invalidate();
        return after;
    }
    private ObjectNode current() {
        return jdbc.queryForObject("select * from hpdi_work_settings where id=1", this::read);
    }
    private ObjectNode read(ResultSet row, int index) throws SQLException {
        ObjectNode result = SettingsValidation.parse(json, row.getString("payload"));
        result.put("version", row.getLong("version"));
        result.put("updatedAt", row.getTimestamp("updated_at").toInstant().toString());
        result.put("updatedBy", row.getString("updated_by"));
        return result;
    }
}
