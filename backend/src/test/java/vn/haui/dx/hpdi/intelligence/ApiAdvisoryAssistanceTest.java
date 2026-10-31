package vn.haui.dx.hpdi.intelligence;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.Test;
import vn.haui.dx.hpdi.intelligence.api.IntelligenceFailure;
import vn.haui.dx.hpdi.intelligence.integration.AssistanceTransport;
import vn.haui.dx.hpdi.intelligence.internal.ApiAdvisoryAssistance;

class ApiAdvisoryAssistanceTest {
    private final ObjectMapper json = new ObjectMapper();
    private final AssistanceTransport transport = mock(AssistanceTransport.class);
    private final ApiAdvisoryAssistance capability = new ApiAdvisoryAssistance(transport, json);
    private final Map<String, String> context = Map.of("title", "machine", "description", "no power");

    @Test void adviceIsExplicitlyUncitedAndAlwaysNeedsReview() {
        when(transport.assist(any())).thenAnswer(call -> {
            var request = (com.fasterxml.jackson.databind.JsonNode)call.getArgument(0);
            return json.createObjectNode().put("contractVersion", "advisory@0.1.0")
                    .put("requestId", request.path("requestId").asText()).put("answer", "Check the external cable.");
        });
        var result = capability.advise("help", context);
        assertEquals("0.2.0", result.contractVersion()); assertEquals("MODEL_ADVICE", result.reasonCode());
        assertTrue(result.needsReview()); assertTrue(result.citations().isEmpty());
    }

    @Test void extraProviderFieldsCannotBecomeWorkflowActionsOrCitations() {
        when(transport.assist(any())).thenAnswer(call -> {
            var request = (com.fasterxml.jackson.databind.JsonNode)call.getArgument(0);
            return json.createObjectNode().put("contractVersion", "advisory@0.1.0")
                    .put("requestId", request.path("requestId").asText()).put("answer", "advice").put("priority", "CRITICAL");
        });
        assertThrows(IntelligenceFailure.class, () -> capability.advise("help", context));
    }

    @Test void mismatchedResponseIdentityIsRejected() {
        when(transport.assist(any())).thenReturn(json.createObjectNode().put("contractVersion", "advisory@0.1.0")
                .put("requestId", java.util.UUID.randomUUID().toString()).put("answer", "advice"));
        assertThrows(IntelligenceFailure.class, () -> capability.advise("help", context));
    }
}
