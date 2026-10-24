package vn.haui.dx.hpdi.intelligence.internal;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import vn.haui.dx.hpdi.common.api.CapabilityException;
import vn.haui.dx.hpdi.data.api.AuthorizedKnowledge;
import vn.haui.dx.hpdi.data.api.KnowledgeCatalog;
import vn.haui.dx.hpdi.human.api.Actor;
import vn.haui.dx.hpdi.human.api.HumanContext;
import vn.haui.dx.hpdi.intelligence.api.AssistanceInput;
import vn.haui.dx.hpdi.intelligence.api.AssistanceOutcome;
import vn.haui.dx.hpdi.intelligence.api.AssistanceRouting;
import vn.haui.dx.hpdi.intelligence.api.GroundedAssistanceCapability;
import vn.haui.dx.hpdi.intelligence.api.IntelligenceFailure;
import vn.haui.dx.hpdi.intelligence.integration.AssistanceTransport;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class TrustedAssistanceCapability implements GroundedAssistanceCapability {
    private static final Set<String> RUNTIME_FIELDS =
            Set.of(
                    "provider",
                    "provider_version",
                    "model",
                    "model_revision",
                    "config_version",
                    "prompt_envelope_version",
                    "app_prompt_version",
                    "output_schema_version");

    private final HumanContext human;
    private final KnowledgeCatalog knowledge;
    private final AssistanceTransport transport;
    private final ObjectMapper json;
    private final AssistanceRouting routing;

    @Autowired
    public TrustedAssistanceCapability(
            HumanContext human,
            KnowledgeCatalog knowledge,
            AssistanceTransport transport,
            ObjectMapper json,
            AssistanceRouting routing) {
        this.human = human;
        this.knowledge = knowledge;
        this.transport = transport;
        this.json = json;
        this.routing = routing;
    }

    public TrustedAssistanceCapability(
            HumanContext human,
            KnowledgeCatalog knowledge,
            AssistanceTransport transport,
            ObjectMapper json) {
        this(human, knowledge, transport, json, null);
    }

    @Override
    public AssistanceOutcome assist(String workspace, AssistanceInput input) {
        var route = input == null ? null : input.route();
        if (route != null && routing == null) throw IntelligenceFailure.configuration();

        try {
            if (route != null) routing.validate(route);
            return assistBound(workspace, input);
        } finally {
            if (route != null) routing.release(route);
        }
    }

    private AssistanceOutcome assistBound(String workspace, AssistanceInput input) {
        Actor actor = actor(workspace);
        validate(input);
        boolean cloud = input.route() != null;
        String version = cloud ? "0.2.0" : "0.1.0";
        if (cloud && !input.route().expectedRuntime().equals(input.generationRuntime()))
            throw IntelligenceFailure.configuration();

        String requestId = UUID.randomUUID().toString();
        var request = json.createObjectNode();
        request.put("contractVersion", version);
        request.put("requestId", requestId);
        request.put("workspaceId", actor.workspace());
        request.put("profileId", input.profileId());
        request.put("profileVersion", input.profileVersion());
        request.put("policyIdentity", input.policyIdentity());
        request.put("question", input.question());
        if (cloud) request.set("routeBinding", json.valueToTree(input.route()));

        var documents = request.putArray("documents");
        Map<UUID, AuthorizedKnowledge> snapshot = new HashMap<>();
        Map<UUID, AssistanceInput.SourcePin> pins = new HashMap<>();
        int totalBytes = 0;

        for (var pin : input.sources()) {
            if (pins.put(pin.metadataId(), pin) != null) throw IntelligenceFailure.configuration();

            var source = resolve(workspace, pin);
            String content = decode(source.content());
            totalBytes += source.content().length;

            if (content.length() > 16000 || totalBytes > 32000)
                throw IntelligenceFailure.configuration();

            snapshot.put(pin.metadataId(), source);
            var document = documents.addObject();
            document.put("metadataId", pin.metadataId().toString());
            document.put("version", source.version());
            document.put("checksum", source.checksum());
            document.put("content", content);
            document.put("mediaType", source.mediaType());
        }

        // No database transaction spans private retrieval or inference.
        JsonNode response;
        try {
            response = transport.assist(request);
        } catch (IntelligenceFailure failure) {
            if (cloud && (failure.status() == 503 || failure.status() == 504)) {
                routing.feedback(input.route(), "UNAVAILABLE", "GENERATION_UNAVAILABLE", 60);
            }
            throw failure;
        }
        var responseFields =
                new HashSet<>(
                        Set.of(
                                "contractVersion",
                                "requestId",
                                "status",
                                "answer",
                                "citations",
                                "needsReview",
                                "reasonCode",
                                "retrievalRuntime",
                                "generationRuntime"));
        if (cloud) responseFields.addAll(Set.of("routeBinding", "routeFeedback"));
        fields(response, responseFields);

        if (!version.equals(text(response, "contractVersion", 10))
                || !requestId.equals(text(response, "requestId", 100))
                || !response.path("needsReview").isBoolean()
                || !response.path("needsReview").booleanValue()) {
            throw IntelligenceFailure.invalid();
        }

        fields(response.get("generationRuntime"), RUNTIME_FIELDS);

        for (var entry : input.generationRuntime().entrySet()) {
            if (!entry.getValue()
                    .equals(text(response.get("generationRuntime"), entry.getKey(), 256))) {
                throw IntelligenceFailure.configuration();
            }
        }

        if (cloud) validateCloudFeedback(input, response);

        validateRetrieval(response.get("retrievalRuntime"));

        var sourceState =
                snapshot.values().stream()
                        .sorted(
                                java.util.Comparator.comparing(
                                        item -> item.metadataId().toString()))
                        .map(
                                item ->
                                        List.of(
                                                item.metadataId().toString(),
                                                item.version(),
                                                item.checksum()))
                        .toList();

        try {
            String corpus =
                    "adapter-snapshot@0.1.0:"
                            + HexFormat.of()
                                    .formatHex(
                                            MessageDigest.getInstance("SHA-256")
                                                    .digest(json.writeValueAsBytes(sourceState)));

            if (!corpus.equals(
                    response.get("retrievalRuntime").get("corpus_version").textValue())) {
                throw IntelligenceFailure.invalid();
            }
        } catch (java.io.IOException | NoSuchAlgorithmException error) {
            throw IntelligenceFailure.configuration();
        }

        String status = text(response, "status", 40);
        String reason = text(response, "reasonCode", 40);
        JsonNode citations = response.get("citations");

        if (!citations.isArray() || citations.size() > 3) throw IntelligenceFailure.invalid();

        if ("DEGRADED".equals(status)) {
            emptyAnswer(response);

            if ("GENERATION_TIMEOUT".equals(reason)) throw IntelligenceFailure.timeout();
            if ("GENERATION_UNAVAILABLE".equals(reason)) throw IntelligenceFailure.unavailable();
            throw IntelligenceFailure.invalid();
        }

        if ("INSUFFICIENT_EVIDENCE".equals(status)) {
            emptyAnswer(response);
            if (!"INSUFFICIENT_EVIDENCE".equals(reason)) throw IntelligenceFailure.invalid();
            sameActor(actor, workspace);
            return new AssistanceOutcome("0.1.0", requestId, status, null, List.of(), true, reason);
        }

        if (!"SUCCESS".equals(status) || !"GROUNDED".equals(reason) || citations.isEmpty()) {
            throw IntelligenceFailure.invalid();
        }

        var rendered = new ArrayList<String>();
        var publicCitations = new ArrayList<AssistanceOutcome.Citation>();
        var selected = new HashSet<UUID>();
        int previousAlias = 0;

        for (var citation : citations) {
            fields(
                    citation,
                    Set.of(
                            "citationId",
                            "metadataId",
                            "sourceVersion",
                            "sourceChecksum",
                            "excerpt"));
            String alias = text(citation, "citationId", 3);

            if (!alias.matches("C[1-3]")) throw IntelligenceFailure.invalid();
            int ordinal = Integer.parseInt(alias.substring(1));
            if (ordinal <= previousAlias) throw IntelligenceFailure.invalid();
            previousAlias = ordinal;

            UUID id;
            try {
                String identifier = text(citation, "metadataId", 36);
                id = UUID.fromString(identifier);
                if (!id.toString().equals(identifier)) throw IntelligenceFailure.invalid();
            } catch (IllegalArgumentException error) {
                throw IntelligenceFailure.invalid();
            }

            var source = snapshot.get(id);
            String excerpt = text(citation, "excerpt", 4096);

            if (source == null
                    || !source.version().equals(text(citation, "sourceVersion", 100))
                    || !source.checksum().equals(text(citation, "sourceChecksum", 64))
                    || !decode(source.content()).contains(excerpt)) {
                throw IntelligenceFailure.invalid();
            }

            selected.add(id);
            rendered.add(excerpt + " [" + alias + "]");
            publicCitations.add(
                    new AssistanceOutcome.Citation(alias, source.title(), source.version()));
        }

        String answer = String.join("\n", rendered);
        if (!answer.equals(text(response, "answer", 16000))) throw IntelligenceFailure.invalid();

        // Re-read authoritative bytes, rights and visibility after inference.
        // Any denial or Core failure propagates without exposing an answer.
        sameActor(actor, workspace);

        for (var id : selected) {
            var current = resolve(workspace, pins.get(id));
            var original = snapshot.get(id);

            if (!current.title().equals(original.title())
                    || !java.util.Arrays.equals(current.content(), original.content())) {
                throw CapabilityException.conflict("Knowledge changed during assistance");
            }
        }

        sameActor(actor, workspace);
        return new AssistanceOutcome(
                "0.1.0", requestId, status, answer, publicCitations, true, reason);
    }

    private Actor actor(String workspace) {
        var actor = human.currentActor(workspace);

        if (actor == null
                || !workspace.equals(actor.workspace())
                || actor.subject() == null
                || actor.subject().isBlank()) {
            throw CapabilityException.forbidden("Trusted workspace actor required");
        }

        return actor;
    }

    private void sameActor(Actor original, String workspace) {
        if (!original.subject().equals(actor(workspace).subject())) {
            throw CapabilityException.forbidden("Workspace actor changed");
        }
    }

    private AuthorizedKnowledge resolve(String workspace, AssistanceInput.SourcePin pin) {
        var source = knowledge.resolve(workspace, pin.metadataId());

        if (source == null
                || !pin.metadataId().equals(source.metadataId())
                || !workspace.equals(source.workspace())
                || !pin.version().equals(source.version())
                || !pin.checksum().equals(source.checksum())
                || !pin.usageRights().equals(source.usageRights())
                || !Set.of("text/plain", "text/markdown").contains(source.mediaType())
                || source.title() == null
                || source.title().isBlank()
                || source.title().length() > 200
                || source.title().codePoints().anyMatch(Character::isISOControl)) {
            throw CapabilityException.conflict("Knowledge identity or rights changed");
        }

        try {
            String checksum =
                    HexFormat.of()
                            .formatHex(
                                    MessageDigest.getInstance("SHA-256").digest(source.content()));
            if (!checksum.equals(pin.checksum()))
                throw CapabilityException.conflict("Knowledge content changed");
        } catch (NoSuchAlgorithmException error) {
            throw IntelligenceFailure.configuration();
        }

        return source;
    }

    private static String decode(byte[] bytes) {
        try {
            return StandardCharsets.UTF_8
                    .newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString();
        } catch (CharacterCodingException error) {
            throw IntelligenceFailure.configuration();
        }
    }

    private static void validate(AssistanceInput input) {
        if (input == null
                || input.question() == null
                || input.question().isBlank()
                || input.question().length() > 2000
                || input.sources().size() > 10
                || !input.generationRuntime().keySet().equals(RUNTIME_FIELDS)
                || !input.policyIdentity()
                        .equals(input.generationRuntime().get("app_prompt_version"))) {
            throw IntelligenceFailure.configuration();
        }

        for (var value :
                List.of(input.profileId(), input.profileVersion(), input.policyIdentity())) {
            if (value.isBlank() || value.length() > 100) throw IntelligenceFailure.configuration();
        }

        for (var pin : input.sources()) {
            if (pin.metadataId() == null
                    || pin.version() == null
                    || pin.version().isBlank()
                    || pin.version().length() > 100
                    || pin.checksum() == null
                    || !pin.checksum().matches("[a-f0-9]{64}")
                    || pin.usageRights() == null
                    || pin.usageRights().isBlank()
                    || pin.usageRights().length() > 200) {
                throw IntelligenceFailure.configuration();
            }
        }
    }

    private static void fields(JsonNode node, Set<String> expected) {
        var actual = new HashSet<String>();
        if (node == null || !node.isObject()) throw IntelligenceFailure.invalid();
        node.fieldNames().forEachRemaining(actual::add);
        if (!actual.equals(expected)) throw IntelligenceFailure.invalid();
    }

    private static String text(JsonNode node, String field, int maximum) {
        var value = node.get(field);
        if (value == null
                || !value.isTextual()
                || value.textValue().isBlank()
                || value.textValue().length() > maximum) {
            throw IntelligenceFailure.invalid();
        }
        return value.textValue();
    }

    private static void emptyAnswer(JsonNode response) {
        if (!response.get("answer").isNull() || !response.get("citations").isEmpty()) {
            throw IntelligenceFailure.invalid();
        }
    }

    private static void validateRetrieval(JsonNode runtime) {
        fields(
                runtime,
                Set.of(
                        "engine",
                        "engine_version",
                        "config_version",
                        "corpus_version",
                        "index_version"));

        if (!"tfidf".equals(text(runtime, "engine", 100))
                || !"0.1.0".equals(text(runtime, "engine_version", 100))
                || !"tfidf-config@0.1.0".equals(text(runtime, "config_version", 100))
                || !text(runtime, "corpus_version", 100)
                        .matches("adapter-snapshot@0.1.0:[a-f0-9]{64}")
                || !text(runtime, "index_version", 64).matches("[a-f0-9]{64}")) {
            throw IntelligenceFailure.invalid();
        }
    }

    private void validateCloudFeedback(AssistanceInput input, JsonNode response) {
        if (!json.valueToTree(input.route()).equals(response.get("routeBinding")))
            throw IntelligenceFailure.invalid();
        var feedback = response.get("routeFeedback");
        fields(
                feedback,
                Set.of("state", "code", "cooldownSeconds", "providerCalls", "latencyBucket"));
        String state = text(feedback, "state", 40);
        String code = text(feedback, "code", 40);
        String status = text(response, "status", 40);
        if (!feedback.path("cooldownSeconds").isInt()
                || feedback.path("cooldownSeconds").intValue() < 0
                || feedback.path("cooldownSeconds").intValue() > 3600
                || !feedback.path("providerCalls").isInt()
                || feedback.path("providerCalls").intValue() < 0
                || feedback.path("providerCalls").intValue() > 1
                || !Set.of("LT_100MS", "LT_1S", "LT_5S", "GE_5S")
                        .contains(text(feedback, "latencyBucket", 20))) {
            throw IntelligenceFailure.invalid();
        }

        if ("READY".equals(state)) {
            if (!"OK".equals(code)
                    || feedback.path("cooldownSeconds").intValue() != 0
                    || !Set.of("SUCCESS", "INSUFFICIENT_EVIDENCE").contains(status))
                throw IntelligenceFailure.invalid();
            routing.feedback(input.route(), state, code, 0);
            return;
        }

        emptyAnswer(response);
        if (!code.equals(text(response, "reasonCode", 40))) throw IntelligenceFailure.invalid();
        boolean limited =
                "RATE_LIMITED".equals(state)
                        && Set.of(
                                        "PROVIDER_RATE_LIMITED",
                                        "PROVIDER_BUDGET_EXCEEDED",
                                        "PROVIDER_POOL_UNAVAILABLE")
                                .contains(code);
        boolean unavailable =
                "UNAVAILABLE".equals(state)
                        && Set.of("GENERATION_TIMEOUT", "GENERATION_UNAVAILABLE").contains(code);
        boolean disabled =
                Set.of("DISABLED", "REVOKED").contains(state)
                        && Set.of(
                                        "PROVIDER_CONFIGURATION_ERROR",
                                        "GENERATION_IDENTITY_MISMATCH",
                                        "EGRESS_NOT_AUTHORIZED",
                                        "GENERATION_CONTRACT_FAILED")
                                .contains(code);
        if (!(limited || unavailable || disabled)
                || !(disabled ? "ERROR" : "DEGRADED").equals(status)) {
            throw IntelligenceFailure.invalid();
        }

        routing.feedback(input.route(), state, code, feedback.path("cooldownSeconds").intValue());
        if (disabled) throw IntelligenceFailure.invalid();
        if ("GENERATION_TIMEOUT".equals(code)) throw IntelligenceFailure.timeout();
        throw IntelligenceFailure.unavailable();
    }
}
