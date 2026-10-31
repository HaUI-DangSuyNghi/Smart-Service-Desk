package vn.haui.dx.hpdi.settings.internal;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import org.springframework.stereotype.Service;
import vn.haui.dx.core.audit.api.AuditService;
import vn.haui.dx.core.identity.api.FreshAdminAuthorization;
import vn.haui.dx.hpdi.settings.api.SettingsAdministration;

@Service
public class CoreSettingsAdministration implements SettingsAdministration {
    private final FreshAdminAuthorization authorization;
    private final AuditService audit;
    public CoreSettingsAdministration(FreshAdminAuthorization authorization, AuditService audit) {
        this.authorization = authorization; this.audit = audit;
    }
    public String requireAdmin() { return authorization.requirePlatformAdmin(); }
    public void audit(String action, JsonNode before, JsonNode after) {
        audit.record(action, "settings", "1", "system:platform", Map.of("before", before, "after", after));
    }
}
