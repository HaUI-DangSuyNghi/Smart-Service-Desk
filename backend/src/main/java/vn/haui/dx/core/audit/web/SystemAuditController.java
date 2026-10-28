package vn.haui.dx.core.audit.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.haui.dx.core.audit.api.AuditQuery;
import vn.haui.dx.core.audit.api.AuditService;
import vn.haui.dx.core.audit.api.AuditEntry;
import vn.haui.dx.core.common.Page;
import vn.haui.dx.core.common.PageRequest;

@RestController
public class SystemAuditController {
    private final AuditService audit;
    public SystemAuditController(AuditService audit) { this.audit = audit; }
    @GetMapping("/api/core/v1/audit/system")
    public Page<AuditEntry> read() {
        return audit.search("system:platform", new AuditQuery(null, null, null, null), PageRequest.of(0, 50));
    }
}
