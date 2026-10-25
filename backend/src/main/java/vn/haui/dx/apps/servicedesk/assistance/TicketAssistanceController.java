package vn.haui.dx.apps.servicedesk.assistance;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.Set;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.haui.dx.hpdi.common.api.CapabilityException;
import vn.haui.dx.hpdi.intelligence.api.AssistanceOutcome;

@RestController
@ConditionalOnProperty(name = "serviceDesk.assistance.enabled", havingValue = "true")
public class TicketAssistanceController {
    private final TicketAssistanceService assistance;
    private final ObjectMapper json;

    public TicketAssistanceController(TicketAssistanceService assistance, ObjectMapper json) {
        this.assistance = assistance;
        this.json = json.copy()
            .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    }

    @PostMapping(
        value = "/api/apps/servicedesk/v1/workspaces/{workspace}/tickets/{id}/assistance",
        consumes = "application/json")
    public ResponseEntity<AssistanceOutcome> assist(
            @PathVariable String workspace, @PathVariable UUID id,
            HttpServletRequest request) throws IOException {
        byte[] bytes = request.getInputStream().readNBytes(16385);

        if (bytes.length > 16384) throw CapabilityException.validation("Assistance request exceeds its limit");

        String question;

        try {
            var payload = json.readTree(bytes);
            var fields = new java.util.HashSet<String>();

            if (payload == null || !payload.isObject()) throw new IllegalArgumentException();
            payload.fieldNames().forEachRemaining(fields::add);

            if (!fields.equals(Set.of("question")) || !payload.get("question").isTextual()) {
                throw new IllegalArgumentException();
            }

            question = payload.get("question").textValue();
        } catch (IOException | IllegalArgumentException error) {
            throw CapabilityException.validation("Assistance accepts only a bounded question");
        }

        return ResponseEntity.ok()
            .header("Cache-Control", "no-store")
            .body(assistance.assist(workspace, id, question));
    }
}
