package vn.haui.dx.hpdi.intelligence.internal;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import vn.haui.dx.hpdi.intelligence.api.AdvisoryAssistanceCapability;
import vn.haui.dx.hpdi.intelligence.api.AssistanceOutcome;
import vn.haui.dx.hpdi.intelligence.api.IntelligenceFailure;
import vn.haui.dx.hpdi.intelligence.integration.AssistanceTransport;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public final class ApiAdvisoryAssistance implements AdvisoryAssistanceCapability {
    private final AssistanceTransport transport;
    private final ObjectMapper json;

    public ApiAdvisoryAssistance(AssistanceTransport transport, ObjectMapper json) {
        this.transport = transport; this.json = json;
    }

    @Override
    public AssistanceOutcome advise(String question, Map<String, String> context) {
        if (question == null || question.isBlank() || question.length() > 2000 || context == null
                || !context.keySet().equals(Set.of("title", "description"))
                || context.values().stream().anyMatch(value -> value == null || value.length() > 4000)) {
            throw IntelligenceFailure.configuration();
        }
        String id = UUID.randomUUID().toString();
        var request = json.createObjectNode();
        request.put("contractVersion", "advisory@0.1.0");
        request.put("requestId", id);
        request.put("question", question);
        request.set("context", json.valueToTree(context));
        var response = transport.assist(request);
        var fields = new java.util.HashSet<String>();
        if (response == null || !response.isObject()) throw IntelligenceFailure.invalid();
        response.fieldNames().forEachRemaining(fields::add);
        if (!fields.equals(Set.of("contractVersion", "requestId", "answer"))
                || !"advisory@0.1.0".equals(response.path("contractVersion").asText())
                || !id.equals(response.path("requestId").asText()) || !response.path("answer").isTextual()) {
            throw IntelligenceFailure.invalid();
        }
        String answer = response.path("answer").textValue();
        if (answer.isBlank() || answer.length() > 16000
                || answer.codePoints().anyMatch(value -> Character.isISOControl(value)
                    && value != '\n' && value != '\r' && value != '\t')) throw IntelligenceFailure.invalid();
        return new AssistanceOutcome("0.2.0", id, "SUCCESS", answer, List.of(), true, "MODEL_ADVICE");
    }
}
