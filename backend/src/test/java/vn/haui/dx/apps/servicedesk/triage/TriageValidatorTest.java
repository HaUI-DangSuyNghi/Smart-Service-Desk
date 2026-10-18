package vn.haui.dx.apps.servicedesk.triage;

import java.util.function.Consumer;
import java.util.stream.Stream;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import static org.junit.jupiter.api.Assertions.*;

class TriageValidatorTest {
    private final TriageValidator validator = new TriageValidator(new ObjectMapper());
    private final TriageRequest request = TriageResponses.request();

    @Test void acceptsRealSchemaShapesAndReviewRequiredSuccess() {
        validator.request(request);
        validator.response(request, TriageResponses.success(request));
        validator.response(request, TriageResponses.degraded(request));
        var response = TriageResponses.success(request);
        ((ObjectNode) response.get("explanation")).put("reasonCode", "INSUFFICIENT_INFORMATION");
        response.put("needsReview", true);
        validator.response(request, response);
        assertThrows(TriageFailure.class, () -> validator.response(request, null));
    }

    static Stream<Consumer<ObjectNode>> invalidSuccesses() {
        return Stream.of(
            node -> node.put("requestId", "foreign"),
            node -> node.put("workspaceId", "lab-b"),
            node -> node.put("profile", "other"),
            node -> node.put("contractVersion", "9"),
            node -> ((ObjectNode) node.get("runtime")).put("profileVersion", "service-desk@0.1.0"),
            node -> ((ObjectNode) node.get("runtime")).put("profileVersion", "service-desk"),
            node -> ((ObjectNode) node.get("evidence").get(0)).put("text", "invented evidence"),
            node -> ((ObjectNode) node.get("evidence").get(0)).put("type", "KNOWLEDGE"),
            node -> ((ObjectNode) node.get("evidence").get(0)).put("field", "subject"),
            node -> node.put("confidence", 0.9),
            node -> ((ObjectNode) node.get("result")).put("confidence", 0.9),
            node -> ((ObjectNode) node.get("runtime")).putNull("rulesVersion"),
            node -> ((ObjectNode) node.get("runtime")).remove("rulesVersion"),
            node -> ((ObjectNode) node.get("explanation")).putArray("matchedRules"),
            node -> ((ObjectNode) node.get("explanation")).putArray("matchedRules").add("same").add("same"),
            node -> ((ObjectNode) node.get("explanation")).put("reasonCode", "INSUFFICIENT_INFORMATION"),
            node -> node.put("needsReview", "false"),
            node -> node.putArray("evidence"),
            node -> node.putNull("result"),
            node -> node.putNull("runtime"),
            node -> node.put("status", "UNKNOWN"),
            node -> node.remove("error"));
    }

    @ParameterizedTest @MethodSource("invalidSuccesses")
    void rejectsSchemaAndSemanticViolations(Consumer<ObjectNode> mutate) {
        var response = TriageResponses.success(request);
        mutate.accept(response);
        assertEquals(502, assertThrows(TriageFailure.class, () -> validator.response(request, response)).status());
    }

    @Test void rejectsInvalidDegradedAndBlankRequest() {
        var response = TriageResponses.degraded(request);
        response.put("needsReview", false);
        assertThrows(TriageFailure.class, () -> validator.response(request, response));
        response.put("needsReview", true).set("result", TriageResponses.success(request).get("result"));
        assertThrows(TriageFailure.class, () -> validator.response(request, response));
        assertThrows(TriageFailure.class, () -> validator.request(new TriageRequest("0.1.0", "id", "service-desk",
            new TriageRequest.Context("lab-a"), new TriageRequest.Input(" ", "description"))));
    }
}
