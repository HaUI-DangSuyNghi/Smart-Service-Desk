package vn.haui.dx.hpdi.intelligence.integration;

import vn.haui.dx.hpdi.intelligence.api.IntelligenceFailure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

public final class TriageResponses {
    private static final ObjectMapper JSON = new ObjectMapper();

    public static TriageRequest.Envelope request() {
        return envelope(new TriageRequest("0.1.0", "server-request", "service-desk", new TriageRequest.Context("lab-a"),
            new TriageRequest.Input("Network support", "Synthetic request")));
    }

    public static TriageRequest.Envelope envelope(TriageRequest request) {
        return new TriageRequest.Envelope(request, new TriageRequest.TrustedContext(request.context().workspaceId(), "verified-test-actor"), "0.2.0");
    }

    public static ObjectNode success(TriageRequest.Envelope envelope) {
        var request = envelope.request();
        var response = JSON.createObjectNode();
        response.put("contractVersion", request.contractVersion()).put("requestId", request.requestId())
            .put("profile", request.profile()).put("workspaceId", request.context().workspaceId())
            .put("status", "SUCCESS").put("needsReview", false).putNull("error");
        response.putObject("result").put("category", "network").put("priority", "P3").put("suggestedGroup", "it-support");
        response.putArray("evidence").addObject().put("type", "INPUT").put("field", "title").put("text", request.input().title());
        var explanation = response.putObject("explanation");
        explanation.putArray("matchedRules").add("network-rule");
        explanation.put("impact", "low").put("urgency", "low").put("reasonCode", "CLASSIFIED").put("text", "Synthetic test only");
        response.putObject("runtime").put("engine", "rules").put("profileVersion", request.profile() + "@" + envelope.profileVersion()).put("rulesVersion", "0.2.0");
        return response;
    }

    public static ObjectNode degraded(TriageRequest.Envelope request) {
        var response = success(request);
        response.put("status", "DEGRADED").put("needsReview", true);
        response.putNull("result").putNull("explanation").putNull("runtime");
        response.putArray("evidence");
        response.putObject("error").put("code", "INTELLIGENCE_UNAVAILABLE");
        return response;
    }
}
