package vn.haui.dx.hpdi.data.web;
import java.util.UUID;
import org.springframework.web.bind.annotation.*;
import vn.haui.dx.hpdi.common.api.Page;
import vn.haui.dx.hpdi.common.api.PageRequest;
import vn.haui.dx.hpdi.data.api.*;
@RestController
@RequestMapping("/api/hpdi/v1/workspaces/{workspace}/knowledge")
public class KnowledgeController {
    private final KnowledgeCatalog knowledge;
    public KnowledgeController(KnowledgeCatalog knowledge) { this.knowledge=knowledge; }
    @GetMapping
    public Page<KnowledgeSummary> search(@PathVariable String workspace, @RequestParam(required = false) String q,
                                         @RequestParam(required = false) Integer page,
                                         @RequestParam(required = false) Integer size) {
        return knowledge.search(workspace, q, PageRequest.of(page, size));
    }
    @GetMapping("/{id}")
    public AuthorizedKnowledge resolve(@PathVariable String workspace, @PathVariable UUID id) {
        return knowledge.resolve(workspace,id);
    }
}
