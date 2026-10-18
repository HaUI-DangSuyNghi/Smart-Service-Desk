package vn.haui.dx.apps.servicedesk.triage;

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

    public void request(TriageRequest request) {
        if (!requestSchema.validate(json.valueToTree(request)).isEmpty()) throw TriageFailure.configuration();
    }

    public void response(TriageRequest request, JsonNode response) {
        if (response == null || !responseSchema.validate(response).isEmpty()) throw TriageFailure.invalid();
        if (!request.contractVersion().equals(response.path("contractVersion").asText())
                || !request.requestId().equals(response.path("requestId").asText())
                || !request.profile().equals(response.path("profile").asText())
                || !request.context().workspaceId().equals(response.path("workspaceId").asText()))
            throw TriageFailure.invalid();
        if ("SUCCESS".equals(response.path("status").asText())
                && !"service-desk@0.2.0".equals(response.path("runtime").path("profileVersion").asText()))
            throw TriageFailure.invalid();
        for (var evidence : response.path("evidence")) {
            String original = "title".equals(evidence.path("field").asText()) ? request.input().title() : request.input().description();
            if (!original.contains(evidence.path("text").asText())) throw TriageFailure.invalid();
        }
    }
}
