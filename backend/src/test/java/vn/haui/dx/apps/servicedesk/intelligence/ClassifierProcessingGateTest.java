package vn.haui.dx.apps.servicedesk.intelligence;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import vn.haui.dx.hpdi.common.api.CapabilityException;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

class ClassifierProcessingGateTest {
    @TempDir Path directory;
    private static final String KEY = "SYNTHETIC_TEST_KEY_NOT_A_REAL_OWNER_KEY";

    private ClassifierProcessingGate gate(String workspace, boolean expired, boolean tamper)
            throws Exception {
        return gate(workspace, expired, tamper, null);
    }

    private ClassifierProcessingGate gate(
            String workspace, boolean expired, boolean tamper, String bundle) throws Exception {
        var json = new ObjectMapper();
        var fields =
                new java.util.LinkedHashMap<String, Object>(
                        Map.of(
                                "purpose",
                                "AI07_ASSISTED_PROCESSING",
                                "workspace",
                                workspace,
                                "modelDigest",
                                "a".repeat(64),
                                "policyDigest",
                                "b".repeat(64),
                                "expiresEpochSecond",
                                expired ? 0L : Instant.now().getEpochSecond() + 600,
                                "issuer",
                                "SYNTHETIC_TEST_ONLY"));
        if (bundle != null) {
            fields.put("assessmentPolicyBundleDigest", bundle);
        }
        byte[] payload = json.writeValueAsBytes(fields);
        var mac = Mac.getInstance("HmacSHA256");
        mac.init(
                new SecretKeySpec(
                        KEY.getBytes(java.nio.charset.StandardCharsets.UTF_8), "HmacSHA256"));
        String signature = tamper ? "0".repeat(64) : HexFormat.of().formatHex(mac.doFinal(payload));
        var path = directory.resolve("synthetic-grant.json");
        Files.write(
                path,
                json.writeValueAsBytes(
                        Map.of(
                                "payload",
                                Base64.getEncoder().encodeToString(payload),
                                "signature",
                                signature)));
        return new ClassifierProcessingGate(
                new ClassifierConfiguration.Settings(
                        ClassifierConfiguration.Mode.ASSISTED,
                        "fixture",
                        "a".repeat(64),
                        "b".repeat(64),
                        path.toString(),
                        KEY),
                json);
    }

    @Test
    void verifiesScopedGrantAndDetectsReplacement() throws Exception {
        assertTrue(gate("lab-a", false, false).verify("lab-a").matches("[0-9a-f]{64}"));
    }

    @Test
    void hierarchicalProcessingNeedsAnIndependentlySignedBundlePin() throws Exception {
        String bundle = "c".repeat(64);
        var legacy = gate("lab-a", false, false);
        assertThrows(CapabilityException.class, () -> legacy.verifyAssessment("lab-a", bundle));
        var scoped = gate("lab-a", false, false, bundle);
        assertEquals(scoped.verify("lab-a"), scoped.verifyAssessment("lab-a", bundle));
        assertThrows(
                CapabilityException.class, () -> scoped.verifyAssessment("lab-a", "d".repeat(64)));
        assertThrows(CapabilityException.class, () -> scoped.verifyAssessment("lab-b", bundle));
        assertThrows(
                CapabilityException.class,
                () -> gate("lab-a", true, false, bundle).verifyAssessment("lab-a", bundle));
        assertThrows(
                CapabilityException.class,
                () -> gate("lab-a", false, true, bundle).verifyAssessment("lab-a", bundle));
    }

    @ParameterizedTest
    @ValueSource(strings = {"workspace", "expired", "signature"})
    void invalidOrForgedGrantCannotAuthorizeProcessing(String mutation) throws Exception {
        var gate =
                gate(
                        mutation.equals("workspace") ? "lab-b" : "lab-a",
                        mutation.equals("expired"),
                        mutation.equals("signature"));
        assertThrows(CapabilityException.class, () -> gate.verify("lab-a"));
    }
}
