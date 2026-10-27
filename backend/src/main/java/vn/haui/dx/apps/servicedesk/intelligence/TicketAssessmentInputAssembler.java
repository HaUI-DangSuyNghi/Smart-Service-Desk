package vn.haui.dx.apps.servicedesk.intelligence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import vn.haui.dx.apps.servicedesk.intelligence.assessment.AssessmentPolicy;
import vn.haui.dx.apps.servicedesk.intelligence.context.ContextPolicyCatalog;
import vn.haui.dx.apps.servicedesk.intelligence.context.ContextProjection;
import vn.haui.dx.apps.servicedesk.intelligence.context.ContextSnapshot;
import vn.haui.dx.hpdi.common.api.CapabilityException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;

/** Input chỉ từ snapshot đã lưu; phần chuẩn hóa không được giả làm literal evidence. */
@Component
public class TicketAssessmentInputAssembler {
    public record SourceMap(String field, int renderStart, int renderEnd, String sourceId,
            int sourceStart, int sourceEnd, long sourceRevision) {}

    public record Input(Map<String, String> fields, List<SourceMap> sourceMap, String inputDigest, boolean unsafe) {
        public Input {
            fields = Map.copyOf(fields);
            sourceMap = List.copyOf(sourceMap);
        }
    }

    private final ObjectMapper json;
    private final JsonNode policy;
    private final AssessmentPolicy assessment;
    private final ContextPolicyCatalog context;
    private final ClassifierConfiguration.Settings classifier;
    private final String rawDigest;
    private final String guardDigest;
    private final List<Pattern> guards;

    public TicketAssessmentInputAssembler(ObjectMapper json, AssessmentPolicy assessment,
            ContextPolicyCatalog context, ClassifierConfiguration.Settings classifier) {
        this.json = json;
        this.assessment = assessment;
        this.context = context;
        this.classifier = classifier;
        byte[] raw = AssessmentPolicy.read("contextual-input-policy-v0.1.json");
        rawDigest = ContextPolicyCatalog.digest(raw);
        guardDigest = ContextPolicyCatalog.digest(AssessmentPolicy.read("guard-policy-v0.1.json"));
        try {
            policy = json.readTree(raw);
            var patterns = new ArrayList<Pattern>();
            for (var guard : json.readTree(AssessmentPolicy.read("guard-policy-v0.1.json")).path("guards")) {
                if (!"SOFT_SIGNAL".equals(guard.path("kind").asText())) {
                    patterns.add(Pattern.compile(guard.path("pattern").asText(), Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE));
                }
            }
            guards = List.copyOf(patterns);
        } catch (java.io.IOException failure) {
            throw new IllegalStateException("Cannot read contextual input policy", failure);
        }
        if (!"servicedesk-contextual-input@0.1.0".equals(policy.path("version").asText())
                || !"DRAFT".equals(policy.path("approvalStatus").asText())
                || policy.path("maxCharacters").asInt() != 8192) {
            throw new IllegalArgumentException("Unsupported contextual input policy");
        }
    }

    public String policyDigest() {
        return ContextPolicyCatalog.digest(rawDigest + ":" + guardDigest + ":" + context.bundleDigest() + ":" + assessment.bundleDigest()
                + ":" + classifier.modelDigest() + ":" + classifier.policyDigest());
    }

    public JsonNode policy() { return policy.deepCopy(); }

    public void verifyUnchanged() {
        assessment.verifyUnchanged();
        if (!rawDigest.equals(ContextPolicyCatalog.digest(AssessmentPolicy.read("contextual-input-policy-v0.1.json")))
                || !guardDigest.equals(ContextPolicyCatalog.digest(AssessmentPolicy.read("guard-policy-v0.1.json")))) {
            throw CapabilityException.conflict("Contextual input policy changed");
        }
    }

    public Input assemble(ContextSnapshot snapshot, ContextProjection projection) {
        var fields = new TreeMap<String, String>();
        var sourceMap = new ArrayList<SourceMap>();
        for (String name : List.of("title", "description")) {
            var source = snapshot.sources().stream().filter(value -> value.sourceId().equals(name)).findFirst().orElse(null);
            String text = source == null ? "" : source.text();
            fields.put(name, text);
            if (!text.isEmpty()) {
                int length = text.codePointCount(0, text.length());
                sourceMap.add(new SourceMap(name, 0, length, name, 0, length, 0));
            }
        }
        var material = new TreeMap<String, Object>();
        for (var item : policy.path("materialSlots")) {
            String slot = item.asText();
            if (projection.usable(slot)) {
                material.put(slot, Map.of("status", "USER_REPORTED", "value", projection.value(slot)));
            }
        }
        try {
            if (!material.isEmpty()) {
                fields.put("description", fields.get("description") + "\n\n" + policy.path("marker").asText()
                        + "\n" + json.writeValueAsString(material));
            }
            if (fields.values().stream().mapToInt(String::length).sum() > 8192
                    || json.writeValueAsBytes(fields).length > policy.path("maxRequestBytes").asInt() - 1024) {
                throw CapabilityException.validation("Augmented input exceeds bounded classification budget");
            }
            boolean unsafe = snapshot.sources().stream().anyMatch(source -> guards.stream()
                    .anyMatch(pattern -> pattern.matcher(source.text()).find()));
            return new Input(fields, sourceMap, ContextPolicyCatalog.digest(json.writeValueAsBytes(fields)), unsafe);
        } catch (com.fasterxml.jackson.core.JsonProcessingException failure) {
            throw new IllegalStateException("Cannot encode contextual input", failure);
        }
    }

    /** Span cắt delimiter hoặc dữ liệu chuẩn hóa không có source-map nên không được công bố. */
    public ContextProjection.Evidence mapSpan(Input input, ContextSnapshot snapshot, String field, int start, int end) {
        for (var mapping : input.sourceMap()) {
            if (mapping.field().equals(field) && start >= mapping.renderStart() && end <= mapping.renderEnd() && end > start) {
                var source = snapshot.sources().stream().filter(value -> value.sourceId().equals(mapping.sourceId())).findFirst().orElseThrow();
                int left = mapping.sourceStart() + start - mapping.renderStart();
                int right = mapping.sourceStart() + end - mapping.renderStart();
                String quote = source.text().substring(source.text().offsetByCodePoints(0, left), source.text().offsetByCodePoints(0, right));
                return new ContextProjection.Evidence(source.sourceId(), mapping.sourceRevision(), quote, left, right, "MAPPED_LITERAL", false);
            }
        }
        throw CapabilityException.validation("Span crosses generated contextual input");
    }
}
