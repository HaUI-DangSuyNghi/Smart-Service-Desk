package vn.haui.dx.apps.servicedesk.intelligence;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import vn.haui.dx.hpdi.common.api.CapabilityException;

import java.io.IOException;
import java.util.UUID;

@RestController
public class TicketClassificationController {
    private final TicketClassificationOrchestrator classifier;
    private final ObjectMapper json;

    public TicketClassificationController(
            TicketClassificationOrchestrator classifier, ObjectMapper json) {
        this.classifier = classifier;
        this.json =
                json.copy()
                        .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
                        .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    }

    @PostMapping(
            value =
                    "/api/apps/servicedesk/v1/workspaces/{workspace}/tickets/{id}/assistant/classify",
            consumes = "application/json")
    public ResponseEntity<TicketClassificationView> classify(
            @PathVariable String workspace, @PathVariable UUID id, HttpServletRequest request)
            throws IOException {
        byte[] raw = request.getInputStream().readNBytes(1025);
        try {
            var body = json.readTree(raw);
            if (raw.length > 1024
                    || body == null
                    || !body.isObject()
                    || body.size() != 1
                    || !body.path("contractVersion").isTextual()
                    || !"model-primary-assessment@0.1.0"
                            .equals(body.path("contractVersion").textValue())) {
                throw new IllegalArgumentException();
            }
        } catch (IOException | IllegalArgumentException failure) {
            throw CapabilityException.validation(
                    "Classification accepts only its contract version");
        }
        return ResponseEntity.ok()
                .header("Cache-Control", "no-store")
                .body(classifier.classify(workspace, id));
    }
}
