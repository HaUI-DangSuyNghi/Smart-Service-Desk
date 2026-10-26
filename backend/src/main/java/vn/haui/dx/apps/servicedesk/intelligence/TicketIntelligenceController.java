package vn.haui.dx.apps.servicedesk.intelligence;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.haui.dx.hpdi.common.api.CapabilityException;

@RestController
@ConditionalOnProperty(name = "serviceDesk.assistant.enabled", havingValue = "true")
public class TicketIntelligenceController {
    private final TicketIntelligenceOrchestrator assistant;
    private final ObjectMapper json;

    public TicketIntelligenceController(TicketIntelligenceOrchestrator assistant, ObjectMapper json) {
        this.assistant = assistant;
        this.json = json.copy()
                .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    }

    @PostMapping(
            value = "/api/apps/servicedesk/v1/workspaces/{workspace}/tickets/{id}/assistant/assess",
            consumes = "application/json")
    public ResponseEntity<TicketIntelligenceView> assess(
            @PathVariable String workspace,
            @PathVariable UUID id,
            HttpServletRequest request) throws IOException {
        byte[] bytes = request.getInputStream().readNBytes(16385);

        if (bytes.length > 16384) {
            throw CapabilityException.validation("Assistant request exceeds its limit");
        }

        TicketAssessmentRequest input = parse(bytes);

        return ResponseEntity.ok()
                .header("Cache-Control", "no-store")
                .body(assistant.assess(workspace, id, input));
    }

    private TicketAssessmentRequest parse(byte[] bytes) {
        try {
            var payload = json.readTree(bytes);
            var fields = new HashSet<String>();

            if (payload == null || !payload.isObject()) {
                throw new IllegalArgumentException();
            }

            payload.fieldNames().forEachRemaining(fields::add);

            if (!Set.of("contractVersion", "includeGuidance", "question").containsAll(fields)
                    || !payload.path("contractVersion").isTextual()
                    || !payload.path("includeGuidance").isBoolean()
                    || (payload.has("question") && !payload.path("question").isTextual())) {
                throw new IllegalArgumentException();
            }

            return new TicketAssessmentRequest(
                    payload.get("contractVersion").textValue(),
                    payload.has("question") ? payload.get("question").textValue() : null,
                    payload.get("includeGuidance").booleanValue());
        } catch (IOException | IllegalArgumentException failure) {
            throw CapabilityException.validation("Assistant accepts only a version, guidance flag and optional question");
        }
    }
}
