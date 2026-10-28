package vn.haui.dx.apps.servicedesk.intelligence.context;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Collections;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** Một bộ policy duy nhất cho runtime, test và đánh giá offline. */
@Component
public final class ContextPolicyCatalog {
    public static final String VERSION = "servicedesk-context@0.1.0";
    private static final String ROOT = "/apps/servicedesk/intelligence/";

    private final JsonNode slots;
    private final JsonNode questions;
    private final JsonNode duplicates;
    private final Map<String, String> digests;
    private final Map<String, Pattern> patterns;
    private final Map<String, Pattern> devices;
    private final Map<String, Pattern> symptoms;
    private final Map<String, Pattern> booleans;

    public ContextPolicyCatalog(ObjectMapper json) {
        try {
            var hashes = new LinkedHashMap<String, String>();
            slots = read(json, "context-slots-v0.1.json", hashes);
            questions = read(json, "enrichment-questions-v0.1.json", hashes);
            duplicates = read(json, "duplicate-policy-v0.1.json", hashes);
            if (!VERSION.equals(slots.path("version").asText())
                    || !"DRAFT".equals(slots.path("approvalStatus").asText())
                    || !"servicedesk-enrichment-questions@0.1.0".equals(questions.path("version").asText())
                    || !"servicedesk-duplicate-policy@0.1.0".equals(duplicates.path("version").asText())
                    || !"DRAFT".equals(duplicates.path("approvalStatus").asText())
                    || duplicates.path("sharedObjectMappingApproved").asBoolean(true)
                    || !"rule-first-cpu@0.1.0".equals(duplicates.path("decisionVersion").asText())
                    || duplicates.path("autoMergeAllowed").asBoolean(true)
                    || duplicates.path("operationalAuthority").asBoolean(true)
                    || !duplicates.path("requireVerifiedCategory").asBoolean()
                    || candidateLimit() < 1 || candidateLimit() > 50
                    || displayLimit() < 1 || displayLimit() > 3
                    || maxEvents() < 1 || maxEvents() > 32
                    || horizonDays() < 1 || horizonDays() > 90
                    || questions.path("maxQuestions").asInt() != 3) {
                throw new IllegalArgumentException("Invalid AI08 policy");
            }
            hashes.put("taxonomy-v0.1.json", digest(readBytes("taxonomy-v0.1.json")));
            hashes.put("assessment-policy-v0.1.json", digest(readBytes("assessment-policy-v0.1.json")));
            digests = Collections.unmodifiableMap(hashes);
            patterns = compileMap(slots.path("patterns"));
            devices = compileMap(slots.path("devices"));
            symptoms = compileMap(slots.path("symptoms"));
            booleans = compileMap(slots.path("booleans"));
        } catch (IOException failure) {
            throw new IllegalStateException("AI08 policy cannot be loaded", failure);
        }
    }

    public int maxEvents() { return slots.path("maxEvents").asInt(); }
    public int candidateLimit() { return duplicates.path("candidateLimit").asInt(); }
    public int displayLimit() { return duplicates.path("displayLimit").asInt(); }
    public int horizonDays() { return duplicates.path("horizonDays").asInt(); }
    public Map<String, String> digests() { return digests; }
    public String bundleDigest() { return digest(digests.toString()); }
    public Map<String, Pattern> patterns() { return patterns; }
    public Map<String, Pattern> devices() { return devices; }
    public Map<String, Pattern> symptoms() { return symptoms; }
    public Pattern grammar(String name) { return compile(slots.path(name).asText()); }

    public Boolean parseBoolean(String value) {
        if (booleans.get("true").matcher(value).matches()) {
            return Boolean.TRUE;
        }
        if (booleans.get("false").matcher(value).matches()) {
            return Boolean.FALSE;
        }
        return null;
    }

    public Set<String> answerSlots() {
        var result = new java.util.LinkedHashSet<String>();
        slots.path("answerSlots").forEach(value -> result.add(value.asText()));
        return Collections.unmodifiableSet(result);
    }

    public List<String> required(String profile) {
        var node = questions.path("profiles").path(profile);
        if (!node.isArray()) {
            throw new IllegalArgumentException("Unknown readiness profile");
        }
        var result = new java.util.ArrayList<String>();
        node.forEach(value -> result.add(value.asText()));
        return List.copyOf(result);
    }

    public String question(String slot, boolean conflict) {
        return conflict ? questions.path("conflictQuestion").asText().formatted(slot)
                : questions.path("questions").path(slot).asText();
    }

    public static String digest(String value) {
        return digest(value.getBytes(StandardCharsets.UTF_8));
    }

    public static String digest(byte[] value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
        } catch (java.security.NoSuchAlgorithmException failure) {
            throw new IllegalStateException(failure);
        }
    }

    private static Map<String, Pattern> compileMap(JsonNode node) {
        var result = new LinkedHashMap<String, Pattern>();
        node.fields().forEachRemaining(entry -> result.put(entry.getKey(), compile(entry.getValue().asText())));
        return Collections.unmodifiableMap(result);
    }

    private static Pattern compile(String expression) {
        if (expression.isBlank() || expression.length() > 2000) {
            throw new IllegalArgumentException("Invalid context grammar");
        }
        return Pattern.compile(expression, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE | Pattern.CANON_EQ);
    }

    private static JsonNode read(ObjectMapper json, String name, Map<String, String> digests) throws IOException {
        byte[] raw = readBytes(name);
        digests.put(name, digest(raw));
        return json.readTree(raw);
    }

    private static byte[] readBytes(String name) throws IOException {
        try (var stream = ContextPolicyCatalog.class.getResourceAsStream(ROOT + name)) {
            if (stream == null) {
                throw new IOException("Missing AI08 resource");
            }
            return stream.readAllBytes();
        }
    }
}
