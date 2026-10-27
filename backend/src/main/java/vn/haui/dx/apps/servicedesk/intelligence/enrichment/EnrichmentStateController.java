package vn.haui.dx.apps.servicedesk.intelligence.enrichment;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.haui.dx.apps.servicedesk.intelligence.context.ContextReadinessController;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@RestController
public class EnrichmentStateController {
    private final EnrichmentLifecycleService lifecycle;
    private final ObjectMapper json;

    public EnrichmentStateController(EnrichmentLifecycleService lifecycle, ObjectMapper json) {
        this.lifecycle = lifecycle;
        this.json = json.copy().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    }

    @GetMapping("/api/apps/servicedesk/v1/workspaces/{workspace}/tickets/{id}/enrichment-state")
    public ResponseEntity<EnrichmentLifecycleService.View> state(@PathVariable String workspace, @PathVariable UUID id) {
        return ResponseEntity.ok().header("Cache-Control", "no-store").body(lifecycle.get(workspace, id));
    }

    @PostMapping(value = "/api/apps/servicedesk/v1/workspaces/{workspace}/tickets/{id}/enrichment-state/evaluate",
            consumes = "application/json")
    public ResponseEntity<EnrichmentLifecycleService.View> process(
            @PathVariable String workspace, @PathVariable UUID id, HttpServletRequest request) throws IOException {
        ContextReadinessController.requireVersion(json, request, EnrichmentLifecycleService.VERSION);
        return ResponseEntity.ok().header("Cache-Control", "no-store").body(lifecycle.process(workspace, id));
    }

    @GetMapping("/api/apps/servicedesk/v1/workspaces/{workspace}/enrichment-pending")
    public ResponseEntity<List<EnrichmentLifecycleService.View>> pending(@PathVariable String workspace) {
        return ResponseEntity.ok().header("Cache-Control", "no-store").body(lifecycle.pending(workspace));
    }
}
