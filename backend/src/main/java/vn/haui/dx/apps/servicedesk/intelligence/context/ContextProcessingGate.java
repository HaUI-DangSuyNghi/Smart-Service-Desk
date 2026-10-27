package vn.haui.dx.apps.servicedesk.intelligence.context;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.stereotype.Component;

import vn.haui.dx.hpdi.common.api.CapabilityException;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.Set;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** Chỉ xác minh grant được cấp độc lập; runtime không có API tự cấp quyền xử lý. */
@Component
public class ContextProcessingGate {
    public static final String OWN_TICKET = "OWN_TICKET";
    public static final String WORKSPACE_REVIEW = "WORKSPACE_REVIEW";

    private final ContextIntelligenceConfiguration.Settings settings;
    private final ContextPolicyCatalog policy;
    private final ObjectMapper json;

    public ContextProcessingGate(
            ContextIntelligenceConfiguration.Settings settings, ContextPolicyCatalog policy, ObjectMapper json) {
        this.settings = settings;
        this.policy = policy;
        this.json = json.copy().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    }

    public String verify(String workspace, String scope) {
        return verify(workspace, scope, null, null);
    }

    public String verifyReadiness(String workspace, String scope, String readinessDigest) {
        requireDigest(readinessDigest);
        return verify(workspace, scope, readinessDigest, null);
    }

    public String verifyContextual(String workspace, String scope, String readinessDigest, String contextualDigest) {
        requireDigest(readinessDigest);
        requireDigest(contextualDigest);
        return verify(workspace, scope, readinessDigest, contextualDigest);
    }

    private String verify(String workspace, String scope, String readinessDigest, String contextualDigest) {
        try {
            if (!policy.bundleDigest().equals(settings.policyBundleDigest())
                    || settings.verificationKey().length() < 32
                    || !Set.of(OWN_TICKET, WORKSPACE_REVIEW).contains(scope)) {
                throw new IllegalArgumentException();
            }
            var path = Path.of(settings.processingReceipt());
            if (Files.isDirectory(path) && !Files.isSymbolicLink(path)) {
                if (!workspace.matches("[a-z0-9-]{1,50}")) throw new IllegalArgumentException();
                path = path.resolve(workspace + "." + scope + ".json");
            }
            if (Files.isSymbolicLink(path) || Files.size(path) > 16384) {
                throw new IllegalArgumentException();
            }
            byte[] raw;
            try (var stream = Files.newInputStream(path)) {
                raw = stream.readNBytes(16385);
            }
            if (raw.length > 16384) {
                throw new IllegalArgumentException();
            }
            var envelope = json.readTree(raw);
            if (!keys(envelope).equals(Set.of("payload", "signature"))
                    || !envelope.path("payload").isTextual() || !envelope.path("signature").isTextual()) {
                throw new IllegalArgumentException();
            }
            byte[] payload = Base64.getDecoder().decode(envelope.path("payload").asText());
            var mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(settings.verificationKey().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            if (!MessageDigest.isEqual(mac.doFinal(payload),
                    HexFormat.of().parseHex(envelope.path("signature").asText()))) {
                throw new IllegalArgumentException();
            }
            var grant = json.readTree(payload);
            var expected = new HashSet<>(Set.of(
                    "purpose", "workspace", "scope", "policyBundleDigest", "expiresEpochSecond", "issuer"));
            for (String optional : Set.of("readinessPolicyDigest", "contextualPolicyDigest")) {
                if (grant.has(optional)) {
                    requireDigest(grant.path(optional).textValue());
                    expected.add(optional);
                }
            }
            if (!keys(grant).equals(expected)
                    || readinessDigest != null && !readinessDigest.equals(grant.path("readinessPolicyDigest").textValue())
                    || contextualDigest != null && !contextualDigest.equals(grant.path("contextualPolicyDigest").textValue())
                    || !"AI08_ADVISORY_PROCESSING".equals(grant.path("purpose").textValue())
                    || !workspace.equals(grant.path("workspace").textValue())
                    || !scope.equals(grant.path("scope").textValue())
                    || !policy.bundleDigest().equals(grant.path("policyBundleDigest").textValue())
                    || !grant.path("expiresEpochSecond").isIntegralNumber()
                    || !grant.path("expiresEpochSecond").canConvertToLong()
                    || grant.path("expiresEpochSecond").longValue() <= Instant.now().getEpochSecond()
                    || !grant.path("issuer").isTextual() || grant.path("issuer").asText().isBlank()) {
                throw new IllegalArgumentException();
            }
            return ContextPolicyCatalog.digest(raw);
        } catch (Exception failure) {
            throw CapabilityException.forbidden("An independently issued AI08 processing grant is required");
        }
    }

    private static void requireDigest(String value) {
        if (value == null || !value.matches("[0-9a-f]{64}")) {
            throw CapabilityException.forbidden("A pinned processing policy is required");
        }
    }

    private static Set<String> keys(JsonNode value) {
        if (value == null || !value.isObject()) {
            throw new IllegalArgumentException();
        }
        var keys = new HashSet<String>();
        value.fieldNames().forEachRemaining(keys::add);
        return keys;
    }
}
