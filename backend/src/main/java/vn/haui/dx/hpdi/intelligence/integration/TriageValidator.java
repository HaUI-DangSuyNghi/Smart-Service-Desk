package vn.haui.dx.hpdi.intelligence.integration;

import vn.haui.dx.hpdi.intelligence.api.IntelligenceFailure;

import java.io.IOException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import org.springframework.stereotype.Component;

@Component
public class TriageValidator {
    private final ObjectMapper json;
    private final JsonSchema requestSchema;
    private final JsonSchema responseSchema;

    public TriageValidator(ObjectMapper json) {
        this.json = json;
        requestSchema = schema("triage-request.schema.json");
        responseSchema = schema("triage-result.schema.json");
    }

    private JsonSchema schema(String name) {
        try (var stream = getClass().getResourceAsStream("/intelligence-contract/" + name)) {
            if (stream == null) throw new IllegalStateException("Missing Intelligence schema");
            return JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012).getSchema(json.readTree(stream));
        } catch (IOException exception) { throw new IllegalStateException("Cannot load Intelligence schema", exception); }
    }

    public void request(TriageRequest.Envelope envelope) {
        var request = envelope.request();
        if (!requestSchema.validate(json.valueToTree(request)).isEmpty()) throw IntelligenceFailure.configuration();
    }

    public void response(TriageRequest.Envelope envelope, JsonNode response) {
        var request = envelope.request();
        if (response == null || !responseSchema.validate(response).isEmpty()) throw IntelligenceFailure.invalid();
        if (!request.contractVersion().equals(response.path("contractVersion").asText())
                || !request.requestId().equals(response.path("requestId").asText())
                || !request.profile().equals(response.path("profile").asText())
                || !request.context().workspaceId().equals(response.path("workspaceId").asText()))
            throw IntelligenceFailure.invalid();
        if ("SUCCESS".equals(response.path("status").asText())
                && !(request.profile() + "@" + envelope.profileVersion()).equals(response.path("runtime").path("profileVersion").asText()))
            throw IntelligenceFailure.invalid();
        for (var evidence : response.path("evidence")) {
            String original = "title".equals(evidence.path("field").asText()) ? request.input().title() : request.input().description();
            if (!original.contains(evidence.path("text").asText())) throw IntelligenceFailure.invalid();
        }
    }
}
