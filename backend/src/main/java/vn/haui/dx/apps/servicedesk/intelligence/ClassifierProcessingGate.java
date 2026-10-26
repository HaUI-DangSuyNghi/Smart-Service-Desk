package vn.haui.dx.apps.servicedesk.intelligence;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.stereotype.Component;

import vn.haui.dx.hpdi.common.api.CapabilityException;

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

/** Verifies an external processing grant. There is deliberately no grant-creation API. */
@Component
public class ClassifierProcessingGate {
    private final ClassifierConfiguration.Settings settings;
    private final ObjectMapper json;

    public ClassifierProcessingGate(ClassifierConfiguration.Settings settings, ObjectMapper json) {
        this.settings = settings;
        this.json =
                json.copy()
                        .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
                        .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    }

    public String verify(String workspace) {
        try {
            if (settings.processingVerificationKey().length() < 32) {
                throw new IllegalArgumentException();
            }
            var path = Path.of(settings.processingReceipt());
            if (Files.isSymbolicLink(path) || Files.size(path) > 16384) {
                throw new IllegalArgumentException();
            }
            byte[] raw = Files.readAllBytes(path);
            var envelope = json.readTree(raw);
            if (!keys(envelope).equals(Set.of("payload", "signature"))
                    || !envelope.path("payload").isTextual()
                    || !envelope.path("signature").isTextual()) {
                throw new IllegalArgumentException();
            }
            byte[] payload = Base64.getDecoder().decode(envelope.get("payload").textValue());
            var mac = Mac.getInstance("HmacSHA256");
            mac.init(
                    new SecretKeySpec(
                            settings.processingVerificationKey()
                                    .getBytes(java.nio.charset.StandardCharsets.UTF_8),
                            "HmacSHA256"));
            if (!MessageDigest.isEqual(
                    mac.doFinal(payload),
                    HexFormat.of().parseHex(envelope.get("signature").textValue()))) {
                throw new IllegalArgumentException();
            }
            var grant = json.readTree(payload);
            if (!keys(grant)
                            .equals(
                                    Set.of(
                                            "purpose",
                                            "workspace",
                                            "modelDigest",
                                            "policyDigest",
                                            "expiresEpochSecond",
                                            "issuer"))
                    || !"AI07_ASSISTED_PROCESSING".equals(grant.path("purpose").textValue())
                    || !workspace.equals(grant.path("workspace").textValue())
                    || !settings.modelDigest().equals(grant.path("modelDigest").textValue())
                    || !settings.policyDigest().equals(grant.path("policyDigest").textValue())
                    || !grant.path("expiresEpochSecond").isIntegralNumber()
                    || grant.get("expiresEpochSecond").longValue() <= Instant.now().getEpochSecond()
                    || !grant.path("issuer").isTextual()
                    || grant.get("issuer").textValue().isBlank()) {
                throw new IllegalArgumentException();
            }
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw));
        } catch (Exception failure) {
            throw CapabilityException.forbidden(
                    "An independently issued processing grant is required");
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
