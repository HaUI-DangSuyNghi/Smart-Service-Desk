package vn.haui.dx.apps.servicedesk.intelligence.context;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import vn.haui.dx.hpdi.common.api.CapabilityException;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

class ContextProcessingGateTest {
    private static final String TEST_KEY = "synthetic-test-only-not-a-deployment-key";
    private final ObjectMapper json = new ObjectMapper();
    private final ContextPolicyCatalog policy = new ContextPolicyCatalog(json);
    @TempDir Path temporary;

    @Test
    void validIndependentGrantPinsWorkspaceScopeAndPolicy() throws Exception {
        Path path = signed(grant());
        var gate = gate(path, policy.bundleDigest());
        assertEquals(64, gate.verify("lab", ContextProcessingGate.OWN_TICKET).length());
        assertThrows(CapabilityException.class, () -> gate.verify("other", ContextProcessingGate.OWN_TICKET));
        assertThrows(CapabilityException.class, () -> gate.verify("lab", ContextProcessingGate.WORKSPACE_REVIEW));
        assertThrows(CapabilityException.class, () -> gate(path, "0".repeat(64)).verify("lab", ContextProcessingGate.OWN_TICKET));
    }

    @Test
    void expiryPurposeAuthorityFieldsAndPolicyDriftAreRejected() throws Exception {
        for (var mutation : Map.<String, Object>of(
                "expiresEpochSecond", Instant.now().minusSeconds(1).getEpochSecond(),
                "purpose", "AI07_ASSISTED_PROCESSING",
                "policyBundleDigest", "0".repeat(64),
                "operationalAuthority", true,
                "workspace", "foreign").entrySet()) {
            var grant = new LinkedHashMap<>(grant());
            grant.put(mutation.getKey(), mutation.getValue());
            var gate = gate(signed(grant), policy.bundleDigest());
            assertThrows(CapabilityException.class, () -> gate.verify("lab", ContextProcessingGate.OWN_TICKET));
        }
    }

    @Test
    void alteredSignatureDuplicateKeysAndOversizedFileAreRejected() throws Exception {
        Path path = signed(grant());
        var original = json.readTree(Files.readAllBytes(path));
        Files.writeString(path, json.writeValueAsString(Map.of(
                "payload", original.path("payload").asText(), "signature", "0".repeat(64))));
        assertThrows(CapabilityException.class, () -> gate(path, policy.bundleDigest()).verify("lab", "OWN_TICKET"));
        Files.writeString(path, "{\"payload\":\"a\",\"payload\":\"b\",\"signature\":\"c\"}");
        assertThrows(CapabilityException.class, () -> gate(path, policy.bundleDigest()).verify("lab", "OWN_TICKET"));
        Files.writeString(path, " ".repeat(16385));
        assertThrows(CapabilityException.class, () -> gate(path, policy.bundleDigest()).verify("lab", "OWN_TICKET"));
    }

    private Map<String, Object> grant() {
        return Map.of("purpose", "AI08_ADVISORY_PROCESSING", "workspace", "lab", "scope", "OWN_TICKET",
                "policyBundleDigest", policy.bundleDigest(),
                "expiresEpochSecond", Instant.now().plusSeconds(300).getEpochSecond(), "issuer", "synthetic-test-issuer");
    }

    private Path signed(Map<String, Object> grant) throws Exception {
        byte[] payload = json.writeValueAsBytes(grant);
        var mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(TEST_KEY.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        Path path = temporary.resolve(java.util.UUID.randomUUID() + ".json");
        Files.write(path, json.writeValueAsBytes(Map.of("payload", Base64.getEncoder().encodeToString(payload),
                "signature", HexFormat.of().formatHex(mac.doFinal(payload)))));
        return path;
    }

    private ContextProcessingGate gate(Path path, String digest) {
        var settings = new ContextIntelligenceConfiguration.Settings(true,
                ContextIntelligenceConfiguration.Mode.ASSISTED, digest, path.toString(), TEST_KEY);
        return new ContextProcessingGate(settings, policy, json);
    }
}
