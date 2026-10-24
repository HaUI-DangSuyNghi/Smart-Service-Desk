package vn.haui.dx.hpdi.intelligence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import vn.haui.dx.hpdi.common.api.CapabilityException;
import vn.haui.dx.hpdi.data.api.AuthorizedKnowledge;
import vn.haui.dx.hpdi.data.api.KnowledgeCatalog;
import vn.haui.dx.hpdi.human.api.Actor;
import vn.haui.dx.hpdi.human.api.HumanContext;
import vn.haui.dx.hpdi.intelligence.api.AssistanceInput;
import vn.haui.dx.hpdi.intelligence.api.IntelligenceFailure;
import vn.haui.dx.hpdi.intelligence.integration.AssistanceTransport;
import vn.haui.dx.hpdi.intelligence.internal.TrustedAssistanceCapability;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TrustedAssistanceCapabilityTest {
    private final ObjectMapper json = new ObjectMapper();
    private final HumanContext human = mock(HumanContext.class);
    private final KnowledgeCatalog knowledge = mock(KnowledgeCatalog.class);
    private final AssistanceTransport transport = mock(AssistanceTransport.class);
    private final UUID id = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private final Map<String, String> runtime = Map.of(
        "provider", "synthetic", "provider_version", "1", "model", "none", "model_revision", "none",
        "config_version", "1", "prompt_envelope_version", "1", "app_prompt_version", "external-policy@1",
        "output_schema_version", "1");

    private AuthorizedKnowledge source;
    private AssistanceInput input;
    private TrustedAssistanceCapability capability;

    @BeforeEach
    void setup() throws Exception {
        byte[] content = "Inspect the device connection.".getBytes(StandardCharsets.UTF_8);
        String checksum = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        source = AuthorizedKnowledge.of(id, "scope-a", "Synthetic guide", "1", checksum,
            "synthetic-test-only", "text/plain", content);
        input = new AssistanceInput("device connection", "external-profile", "1", "external-policy@1",
            List.of(new AssistanceInput.SourcePin(id, "1", checksum, source.usageRights())), runtime);
        capability = new TrustedAssistanceCapability(human, knowledge, transport, json);

        when(human.currentActor("scope-a")).thenReturn(new Actor("actor-a", "scope-a", Set.of("reader"), Set.of()));
        when(knowledge.resolve("scope-a", id)).thenReturn(source);
        doAnswer(call -> success(call.getArgument(0))).when(transport).assist(any());
    }

    private ObjectNode success(JsonNode request) throws Exception {
        var response = json.createObjectNode();
        response.put("contractVersion", "0.1.0");
        response.put("requestId", request.path("requestId").asText());
        response.put("status", "SUCCESS");
        response.put("answer", "Inspect the device connection. [C1]");
        response.put("needsReview", true);
        response.put("reasonCode", "GROUNDED");
        response.set("generationRuntime", json.valueToTree(runtime));
        String corpusDigest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(
            json.writeValueAsBytes(List.of(List.of(id.toString(), source.version(), source.checksum())))));
        response.set("retrievalRuntime", json.valueToTree(Map.of(
            "engine", "tfidf", "engine_version", "0.1.0", "config_version", "tfidf-config@0.1.0",
            "corpus_version", "adapter-snapshot@0.1.0:" + corpusDigest, "index_version", "b".repeat(64))));

        var citation = response.putArray("citations").addObject();
        citation.put("citationId", "C1");
        citation.put("metadataId", id.toString());
        citation.put("sourceVersion", source.version());
        citation.put("sourceChecksum", source.checksum());
        citation.put("excerpt", "Inspect the device connection.");
        return response;
    }

    @Test
    void authorizesBeforeTransportAndRechecksSelectedSourceWithoutPublishingIdentifiers() {
        var outcome = capability.assist("scope-a", input);

        assertEquals("SUCCESS", outcome.status());
        assertTrue(outcome.needsReview());
        assertEquals("Synthetic guide", outcome.citations().get(0).displayTitle());
        assertFalse(outcome.toString().contains(id.toString()));

        var order = inOrder(human, knowledge, transport);
        order.verify(human).currentActor("scope-a");
        order.verify(knowledge).resolve("scope-a", id);
        order.verify(transport).assist(argThat(request -> !request.toString().contains("actor-a")));
        order.verify(human).currentActor("scope-a");
        order.verify(knowledge).resolve("scope-a", id);
        order.verify(human).currentActor("scope-a");
    }

    @Test
    void deniedActorOrRightsCannotReachPrivateRuntime() {
        when(human.currentActor("scope-a")).thenThrow(CapabilityException.forbidden("Denied"));
        assertThrows(CapabilityException.class, () -> capability.assist("scope-a", input));
        verifyNoInteractions(knowledge, transport);
    }

    @ParameterizedTest
    @ValueSource(strings = {"version", "checksum", "rights", "removed", "outage", "foreign", "content"})
    void changedOrDeniedSourceAfterInferenceSuppressesEntireAnswer(String change) {
        AuthorizedKnowledge changed = switch (change) {
            case "version" -> AuthorizedKnowledge.of(id, "scope-a", source.title(), "2",
                source.checksum(), source.usageRights(), source.mediaType(), source.content());
            case "checksum" -> AuthorizedKnowledge.of(id, "scope-a", source.title(), "1",
                "f".repeat(64), source.usageRights(), source.mediaType(), source.content());
            case "rights" -> AuthorizedKnowledge.of(id, "scope-a", source.title(), "1",
                source.checksum(), "revoked", source.mediaType(), source.content());
            case "foreign" -> AuthorizedKnowledge.of(id, "scope-b", source.title(), "1",
                source.checksum(), source.usageRights(), source.mediaType(), source.content());
            case "content" -> AuthorizedKnowledge.of(id, "scope-a", source.title(), "1",
                source.checksum(), source.usageRights(), source.mediaType(), "changed".getBytes(StandardCharsets.UTF_8));
            default -> null;
        };

        doAnswer(call -> {
            var response = success(call.getArgument(0));
            if ("removed".equals(change)) when(knowledge.resolve("scope-a", id)).thenThrow(CapabilityException.notFound("Removed"));
            else if ("outage".equals(change)) when(knowledge.resolve("scope-a", id)).thenThrow(IntelligenceFailure.unavailable());
            else when(knowledge.resolve("scope-a", id)).thenReturn(changed);
            return response;
        }).when(transport).assist(any());

        assertThrows(RuntimeException.class, () -> capability.assist("scope-a", input));
        verify(transport).assist(any());
    }

    @Test
    void membershipRevocationAfterInferenceCannotReturnSuccess() {
        doAnswer(call -> {
            when(human.currentActor("scope-a")).thenThrow(CapabilityException.forbidden("Revoked"));
            return success(call.getArgument(0));
        }).when(transport).assist(any());

        assertThrows(CapabilityException.class, () -> capability.assist("scope-a", input));
        verify(knowledge, times(1)).resolve("scope-a", id);
    }

    @ParameterizedTest
    @ValueSource(strings = {"alias", "foreign", "prose", "runtime", "extra", "review", "correlation", "refusal-answer", "degraded-answer"})
    void untrustedResponseCannotBypassContract(String mutation) {
        doAnswer(call -> {
            var response = success(call.getArgument(0));
            switch (mutation) {
                case "alias" -> ((ObjectNode) response.path("citations").get(0)).put("citationId", "C99");
                case "foreign" -> ((ObjectNode) response.path("citations").get(0)).put("metadataId", UUID.randomUUID().toString());
                case "prose" -> response.put("answer", "Fabricated unsupported guidance");
                case "runtime" -> ((ObjectNode) response.get("generationRuntime")).put("model", "changed");
                case "extra" -> response.put("tool", "execute");
                case "review" -> response.put("needsReview", false);
                case "correlation" -> response.put("requestId", UUID.randomUUID().toString());
                case "refusal-answer" -> response.put("status", "INSUFFICIENT_EVIDENCE");
                case "degraded-answer" -> response.put("status", "DEGRADED");
            }
            return response;
        }).when(transport).assist(any());

        assertThrows(IntelligenceFailure.class, () -> capability.assist("scope-a", input));
        verify(knowledge, times(1)).resolve("scope-a", id);
    }
}
