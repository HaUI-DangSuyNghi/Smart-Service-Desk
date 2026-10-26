package vn.haui.dx.apps.servicedesk.intelligence.assessment;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** One authoritative resource set; no independently maintained Python policy. */
@Component
public final class AssessmentPolicy {
    public static final String ROOT = "/apps/servicedesk/intelligence/";
    private final JsonNode policy;
    private final JsonNode priority;
    private final Map<String, String> digests;

    public AssessmentPolicy(ObjectMapper mapper) {
        var json =
                mapper.copy()
                        .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
                        .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
        var identities = new LinkedHashMap<String, String>();
        try {
            byte[] assessment = read("assessment-policy-v0.1.json");
            byte[] matrix = read("triage-policy.json");
            identities.put("assessment", sha256(assessment));
            identities.put("priority", sha256(matrix));
            identities.put("taxonomy", sha256(read("taxonomy-v0.1.json")));
            policy = json.readTree(assessment);
            priority = json.readTree(matrix).path("matrix");
            if (!"servicedesk-assessment@0.1.1".equals(policy.path("version").asText())
                    || !"DRAFT".equals(policy.path("riskRubricStatus").asText())
                    || !"DRAFT".equals(policy.path("approvalStatus").asText())
                    || !"triage-policy.json".equals(policy.path("prioritySource").asText())
                    || priority.size() != 4) {
                throw new IllegalArgumentException("Invalid assessment policy");
            }
            for (String impact : List.of("I1", "I2", "I3", "I4")) {
                if (priority.path(impact).size() != 4) {
                    throw new IllegalArgumentException("Incomplete priority matrix");
                }
                for (String urgency : List.of("U1", "U2", "U3", "U4")) {
                    if (!List.of("LOW", "MEDIUM", "HIGH", "CRITICAL")
                            .contains(priority.path(impact).path(urgency).asText())) {
                        throw new IllegalArgumentException("Invalid priority cell");
                    }
                }
            }
            for (String slot : List.of("impact", "urgency", "risk")) {
                var expected =
                        switch (slot) {
                            case "impact" -> java.util.Set.of("I1", "I2", "I3", "I4");
                            case "urgency" -> java.util.Set.of("U1", "U2", "U3", "U4");
                            default ->
                                    java.util.Set.of(
                                            "SECURITY_EXPOSURE",
                                            "DATA_LOSS",
                                            "SAFETY",
                                            "SERVICE_CONTINUITY",
                                            "WIDESPREAD_INCIDENT_POSSIBLE");
                        };
                if (!signals(slot).keySet().equals(expected)) {
                    throw new IllegalArgumentException("Invalid assessment signals");
                }
            }
            for (String parent : List.of("ACCOUNT", "HARDWARE", "NETWORK", "SOFTWARE", "OTHER")) {
                if (requiredSlots(parent).isEmpty()) {
                    throw new IllegalArgumentException("Missing required slots");
                }
            }
            for (String slot : List.of("impact", "urgency", "subCategory")) {
                if (question(slot).isBlank() || question(slot).length() > 250) {
                    throw new IllegalArgumentException("Invalid clarification question");
                }
            }
            digests = Map.copyOf(identities);
        } catch (IOException failure) {
            throw new IllegalStateException("Assessment policy cannot be loaded", failure);
        }
    }

    public Map<String, List<String>> signals(String slot) {
        var result = new LinkedHashMap<String, List<String>>();
        policy.path("signals")
                .path(slot)
                .fields()
                .forEachRemaining(
                        entry -> {
                            var phrases = new ArrayList<String>();
                            entry.getValue()
                                    .forEach(
                                            value -> {
                                                if (!value.isTextual()
                                                        || value.asText().isBlank()
                                                        || value.asText().length() > 250) {
                                                    throw new IllegalArgumentException(
                                                            "Invalid signal phrase");
                                                }
                                                phrases.add(value.asText());
                                            });
                            result.put(entry.getKey(), List.copyOf(phrases));
                        });
        return java.util.Collections.unmodifiableMap(result);
    }

    public JsonNode structuredExtraction() {
        return policy.path("structuredExtraction").deepCopy();
    }

    public String version() {
        return policy.path("version").asText();
    }

    public String priority(String impact, String urgency) {
        return impact == null || urgency == null
                ? null
                : priority.path(impact).path(urgency).textValue();
    }

    public String question(String slot) {
        return policy.path("questions").path(slot).asText();
    }

    public List<String> requiredSlots(String parent) {
        if (parent == null) {
            return List.of("impact", "urgency", "subCategory");
        }
        var slots = new ArrayList<String>();
        policy.path("requiredSlots")
                .path(parent)
                .forEach(
                        value -> {
                            if (!value.isTextual()
                                    || !List.of("impact", "urgency", "subCategory")
                                            .contains(value.asText())
                                    || slots.contains(value.asText())) {
                                throw new IllegalArgumentException("Invalid required slot");
                            }
                            slots.add(value.asText());
                        });
        if (!slots.contains("impact") || !slots.contains("urgency")) {
            throw new IllegalArgumentException("Impact and urgency are required across categories");
        }
        return List.copyOf(slots);
    }

    public Map<String, String> digests() {
        return digests;
    }

    public String bundleDigest() {
        return sha256(
                (digests.get("taxonomy")
                                + ":"
                                + digests.get("assessment")
                                + ":"
                                + digests.get("priority"))
                        .getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    public void verifyUnchanged() {
        if (!digests.get("assessment").equals(sha256(read("assessment-policy-v0.1.json")))
                || !digests.get("taxonomy").equals(sha256(read("taxonomy-v0.1.json")))
                || !digests.get("priority").equals(sha256(read("triage-policy.json")))) {
            throw new IllegalStateException("Assessment policy changed");
        }
    }

    public static byte[] read(String name) {
        try (var stream = AssessmentPolicy.class.getResourceAsStream(ROOT + name)) {
            if (stream == null) {
                throw new IllegalStateException("Missing policy resource: " + name);
            }
            return stream.readAllBytes();
        } catch (IOException failure) {
            throw new IllegalStateException("Cannot read policy", failure);
        }
    }

    public static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException failure) {
            throw new IllegalStateException(failure);
        }
    }
}
