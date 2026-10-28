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
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@RestController
public class ContextIntelligenceController {
    private final ContextIntelligenceService service;
    private final ObjectMapper json;

    public ContextIntelligenceController(ContextIntelligenceService service, ObjectMapper json) {
        this.service = service;
        this.json = json.copy().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    }

    @PostMapping(value = "/api/apps/servicedesk/v1/workspaces/{workspace}/tickets/{id}/assistant/enrichment",
            consumes = "application/json")
    public ResponseEntity<ContextIntelligenceService.EnrichmentView> enrich(
            @PathVariable String workspace, @PathVariable UUID id, HttpServletRequest request) throws IOException {
        String profile = read(request, ContextIntelligenceService.ENRICHMENT_VERSION, true);
        var result = service.enrich(workspace, id, profile);
        bounded(result);
        return ResponseEntity.ok().header("Cache-Control", "no-store").body(result);
    }

    @PostMapping(value = "/api/apps/servicedesk/v1/workspaces/{workspace}/tickets/{id}/assistant/duplicates",
            consumes = "application/json")
    public ResponseEntity<ContextIntelligenceService.DuplicateView> duplicates(
            @PathVariable String workspace, @PathVariable UUID id, HttpServletRequest request) throws IOException {
        read(request, ContextIntelligenceService.DUPLICATE_VERSION, false);
        var result = service.duplicates(workspace, id);
        bounded(result);
        return ResponseEntity.ok().header("Cache-Control", "no-store").body(result);
    }

    private void bounded(Object response) throws IOException {
        if (json.writeValueAsBytes(response).length > 262144) {
            throw CapabilityException.conflict("Advisory output limit reached; manual review required");
        }
    }

    private String read(HttpServletRequest request, String version, boolean enrichment) throws IOException {
        byte[] raw = request.getInputStream().readNBytes(1025);
        try {
            var body = json.readTree(raw);
            var keys = new HashSet<String>();
            if (body == null || !body.isObject() || raw.length > 1024) {
                throw new IllegalArgumentException();
            }
            body.fieldNames().forEachRemaining(keys::add);
            var allowed = enrichment ? Set.of("contractVersion", "readinessProfile") : Set.of("contractVersion");
            if (!allowed.containsAll(keys) || !version.equals(body.path("contractVersion").textValue())) {
                throw new IllegalArgumentException();
            }
            String profile = body.has("readinessProfile") ? body.path("readinessProfile").textValue() : "R10_DUPLICATE";
            if (!Set.of("R10_DUPLICATE", "ASSESSMENT").contains(profile)) {
                throw new IllegalArgumentException();
            }
            return profile;
        } catch (IOException | IllegalArgumentException | NullPointerException failure) {
            throw CapabilityException.validation("Invalid AI08 advisory request");
        }
    }
}
