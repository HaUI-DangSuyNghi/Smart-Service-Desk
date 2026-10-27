package vn.haui.dx.apps.servicedesk.intelligence;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.haui.dx.apps.servicedesk.intelligence.context.ContextReadinessController;

import java.io.IOException;
import java.util.UUID;

@RestController
public class TicketContextualAssessmentController {
    private final TicketContextualAssessmentService service;
    private final ObjectMapper json;

    public TicketContextualAssessmentController(TicketContextualAssessmentService service, ObjectMapper json) {
        this.service = service;
        this.json = json.copy().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    }

    @PostMapping(value = "/api/apps/servicedesk/v1/workspaces/{workspace}/tickets/{id}/assistant/assessment-contextual",
            consumes = "application/json")
    public ResponseEntity<TicketContextualAssessmentService.View> assess(
            @PathVariable String workspace, @PathVariable UUID id, HttpServletRequest request) throws IOException {
        ContextReadinessController.requireVersion(json, request, TicketContextualAssessmentService.VERSION);
        var result = service.assess(workspace, id);
        if (json.writeValueAsBytes(result).length > 262144) {
            throw vn.haui.dx.hpdi.common.api.CapabilityException.conflict("Contextual response limit reached; manual review required");
        }
        return ResponseEntity.ok().header("Cache-Control", "no-store").body(result);
    }
}
