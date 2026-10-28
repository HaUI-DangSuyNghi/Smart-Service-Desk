package vn.haui.dx.apps.servicedesk.intelligence.context;

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
public class ContextReadinessController {
    private final ContextReadinessService service;
    private final ObjectMapper json;

    public ContextReadinessController(ContextReadinessService service, ObjectMapper json) {
        this.service = service;
        this.json = json.copy().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    }

    @PostMapping(value = "/api/apps/servicedesk/v1/workspaces/{workspace}/tickets/{id}/assistant/readiness",
            consumes = "application/json")
    public ResponseEntity<ContextReadinessView> readiness(
            @PathVariable String workspace, @PathVariable UUID id, HttpServletRequest request) throws IOException {
        requireVersion(json, request, ContextReadinessView.VERSION);
        return ResponseEntity.ok().header("Cache-Control", "no-store").body(service.assess(workspace, id));
    }

    public static void requireVersion(ObjectMapper json, HttpServletRequest request, String version) throws IOException {
        byte[] raw = request.getInputStream().readNBytes(1025);
        try {
            var body = json.readTree(raw);
            if (raw.length > 1024 || body == null || !body.isObject() || body.size() != 1
                    || !version.equals(body.path("contractVersion").textValue())) {
                throw new IllegalArgumentException();
            }
        } catch (IOException | IllegalArgumentException failure) {
            throw CapabilityException.validation("Only the declared contract version is accepted");
        }
    }
}
