package vn.haui.dx.hpdi.intelligence.integration;

import com.fasterxml.jackson.databind.JsonNode;

public interface AssistanceTransport {
    JsonNode assist(JsonNode request);
    default void streamKnowledge(JsonNode request, java.util.function.Consumer<JsonNode> events) {
        throw vn.haui.dx.hpdi.intelligence.api.IntelligenceFailure.unavailable();
    }
}
