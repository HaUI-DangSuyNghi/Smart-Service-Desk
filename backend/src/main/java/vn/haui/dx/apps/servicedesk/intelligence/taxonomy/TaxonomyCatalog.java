package vn.haui.dx.apps.servicedesk.intelligence.taxonomy;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.stereotype.Component;

import vn.haui.dx.apps.servicedesk.intelligence.assessment.AssessmentPolicy;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component
public final class TaxonomyCatalog {
    public static final Set<String> PARENTS =
            Set.of("ACCOUNT", "HARDWARE", "NETWORK", "SOFTWARE", "OTHER");

    public record Child(String id, String parent, List<String> phrases, boolean active) {
        public Child {
            phrases = List.copyOf(phrases);
        }
    }

    private final List<Child> children;

    @org.springframework.beans.factory.annotation.Autowired
    public TaxonomyCatalog(ObjectMapper mapper) {
        this(parse(mapper));
    }

    public TaxonomyCatalog(JsonNode root) {
        String version = "servicedesk-taxonomy@0.1.0";
        var parents = new HashSet<String>();
        root.path("mainCategories").forEach(value -> parents.add(value.asText()));
        if (!version.equals(root.path("version").asText())
                || !parents.equals(PARENTS)
                || root.path("mainCategories").size() != PARENTS.size()
                || !"DRAFT".equals(root.path("approvalStatus").asText())
                || !root.path("children").isArray()
                || root.path("children").isEmpty()) {
            throw new IllegalArgumentException("Invalid taxonomy header");
        }
        var ids = new HashSet<String>();
        var entries = new ArrayList<Child>();
        for (var child : root.path("children")) {
            String id = child.path("id").asText();
            String parent = child.path("parent").asText();
            if (!id.matches("[A-Z][A-Z_]{1,60}")
                    || !ids.add(id)
                    || PARENTS.contains(id)
                    || !PARENTS.contains(parent)
                    || !version.equals(child.path("version").asText())
                    || child.path("definition").asText().isBlank()
                    || !child.path("active").isBoolean()
                    || !child.path("positiveExamples").isArray()
                    || !child.path("counterExamples").isArray()
                    || !child.path("requiredEvidence").isArray()) {
                throw new IllegalArgumentException("Invalid taxonomy child");
            }
            var phrases = new ArrayList<String>();
            child.path("requiredEvidence")
                    .forEach(
                            value -> {
                                if (!value.isTextual()
                                        || value.asText().isBlank()
                                        || value.asText().length() > 250) {
                                    throw new IllegalArgumentException("Invalid child evidence");
                                }
                                phrases.add(value.asText());
                            });
            boolean active = child.path("active").booleanValue();
            if (active && phrases.isEmpty()) {
                throw new IllegalArgumentException("Active child requires explicit evidence");
            }
            entries.add(new Child(id, parent, phrases, active));
        }
        children = List.copyOf(entries);
    }

    public List<Child> children() {
        return children;
    }

    private static JsonNode parse(ObjectMapper mapper) {
        try {
            return mapper.copy()
                    .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
                    .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                    .readTree(AssessmentPolicy.read("taxonomy-v0.1.json"));
        } catch (IOException failure) {
            throw new IllegalStateException("Taxonomy cannot be loaded", failure);
        }
    }
}
