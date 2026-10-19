package vn.haui.dx.hpdi.data.web;
import java.util.UUID;
import org.springframework.web.bind.annotation.*;
import vn.haui.dx.hpdi.data.api.*;
@RestController
@RequestMapping("/api/hpdi/v1/workspaces/{workspace}/knowledge")
public class KnowledgeController {
    private final KnowledgeCatalog knowledge;
    public KnowledgeController(KnowledgeCatalog knowledge) { this.knowledge=knowledge; }
    @GetMapping("/{id}")
    public AuthorizedKnowledge resolve(@PathVariable String workspace, @PathVariable UUID id) {
        return knowledge.resolve(workspace,id);
    }
}
