package vn.haui.dx.hpdi.data.web;
import java.util.UUID;
import org.springframework.web.bind.annotation.*;
import vn.haui.dx.hpdi.common.api.Page;
import vn.haui.dx.hpdi.common.api.PageRequest;
import vn.haui.dx.hpdi.data.api.*;
import vn.haui.dx.hpdi.human.api.PermissionGuard;
import vn.haui.dx.hpdi.human.api.Permissions;
/** Knowledge Hub; mọi API cần quyền xem tài liệu (Keycloak). Tải lên đi qua Core (kiểm vai trò). */
@RestController
@RequestMapping("/api/hpdi/v1/workspaces/{workspace}/knowledge")
public class KnowledgeController {
    private final KnowledgeCatalog knowledge;
    private final PermissionGuard permissions;
    public KnowledgeController(KnowledgeCatalog knowledge, PermissionGuard permissions) {
        this.knowledge = knowledge; this.permissions = permissions;
    }
    @GetMapping
    public Page<KnowledgeSummary> search(@PathVariable String workspace, @RequestParam(required = false) String q,
                                         @RequestParam(required = false) Integer page,
                                         @RequestParam(required = false) Integer size) {
        permissions.require(workspace, Permissions.DOCUMENT_VIEW);
        return knowledge.search(workspace, q, PageRequest.of(page, size));
    }
    @GetMapping("/{id}")
    public Preview resolve(@PathVariable String workspace, @PathVariable UUID id) {
        permissions.require(workspace, Permissions.DOCUMENT_VIEW);
        var source = knowledge.resolve(workspace, id);
        return new Preview(source.metadataId(), source.title(), source.version(), source.mediaType(), source.text());
    }
    public record Preview(UUID metadataId, String title, String version, String mediaType, String text) {}
    @GetMapping("/{id}/record")
    public KnowledgeRecord record(@PathVariable String workspace, @PathVariable UUID id) {
        permissions.require(workspace, Permissions.DOCUMENT_VIEW);
        return knowledge.record(workspace, id);
    }
}
