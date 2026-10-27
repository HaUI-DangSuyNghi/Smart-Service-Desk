package vn.haui.dx.apps.servicedesk.clarification;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import vn.haui.dx.hpdi.common.api.CapabilityException;

import java.io.IOException;
import java.util.UUID;

@RestController
public class TicketClarificationController {
    private final TicketClarificationService service;
    private final ObjectMapper json;

    public TicketClarificationController(TicketClarificationService service, ObjectMapper json) {
        this.service = service;
        this.json = json.copy().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
                .disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT)
                .disable(com.fasterxml.jackson.databind.MapperFeature.ALLOW_COERCION_OF_SCALARS);
    }

    @PostMapping(value = "/api/apps/servicedesk/v1/workspaces/{workspace}/tickets/{id}/clarifications",
            consumes = "application/json")
    public ResponseEntity<TicketClarificationService.Result> append(
            @PathVariable String workspace, @PathVariable UUID id, HttpServletRequest request)
            throws IOException {
        byte[] raw = request.getInputStream().readNBytes(16385);
        ClarificationRequest input;
        try {
            if (raw.length > 16384) {
                throw new IllegalArgumentException();
            }
            input = json.readValue(raw, ClarificationRequest.class);
        } catch (IOException | IllegalArgumentException failure) {
            throw CapabilityException.validation("Invalid clarification body");
        }
        var result = service.append(workspace, id, input);
        return ResponseEntity.status(result.replayed() ? 200 : 201)
                .header("Cache-Control", "no-store").body(result);
    }

    @GetMapping("/api/apps/servicedesk/v1/workspaces/{workspace}/tickets/{id}/context-revision")
    public ResponseEntity<TicketClarificationService.Revision> revision(
            @PathVariable String workspace, @PathVariable UUID id) {
        return ResponseEntity.ok().header("Cache-Control", "no-store").body(service.revision(workspace, id));
    }
}
