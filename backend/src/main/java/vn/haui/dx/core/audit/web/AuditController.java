package vn.haui.dx.core.audit.web;

import java.time.Instant;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.haui.dx.core.audit.api.AuditEntry;
import vn.haui.dx.core.audit.api.AuditQuery;
import vn.haui.dx.core.audit.api.AuditService;
import vn.haui.dx.core.common.Page;
import vn.haui.dx.core.common.PageRequest;
import vn.haui.dx.core.identity.api.RequirePermission;

@RestController
@RequestMapping("/api/core/v1/workspaces/{workspace}/audit")
public class AuditController {
    private final AuditService audit;
    public AuditController(AuditService audit) { this.audit = audit; }

    /** Mới nhất trước; quyền kiểm trong AuditService. */
    @GetMapping
    @RequirePermission("audit:view")
    public Page<AuditEntry> search(@PathVariable String workspace,
        @RequestParam(required = false) Instant from, @RequestParam(required = false) Instant to,
        @RequestParam(required = false) String action, @RequestParam(required = false) String actor,
        @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size) {
        return audit.search(workspace, new AuditQuery(from, to, action, actor), PageRequest.of(page, size));
    }
}
