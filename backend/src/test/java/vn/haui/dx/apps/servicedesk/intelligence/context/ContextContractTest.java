package vn.haui.dx.apps.servicedesk.intelligence.context;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;

import org.junit.jupiter.api.Test;

import vn.haui.dx.apps.servicedesk.intelligence.assessment.AssessmentPolicy;
import vn.haui.dx.apps.servicedesk.intelligence.duplicate.DuplicateDecisionEngine;

import java.util.List;
import java.util.UUID;

class ContextContractTest {
    private final ObjectMapper json = new ObjectMapper();
    private final ContextPolicyCatalog policy = new ContextPolicyCatalog(json);

    @Test
    void enrichmentRejectsAuthorityUnknownFieldsAndNonliteralSourceReferences() throws Exception {
        var id = UUID.randomUUID();
        var snapshot = new ContextSnapshot("lab", id, 0, List.of(
                new ContextSnapshot.Source("title", 0, "Máy 15 phòng 1304 không lên nguồn", null, false)), "a".repeat(64));
        var context = new ContextSlotExtractor(policy, new AssessmentPolicy(json)).extract(snapshot);
        var view = new ContextIntelligenceService.EnrichmentView(
                ContextIntelligenceService.ENRICHMENT_VERSION, "REVIEW_REQUIRED", "ASSISTED", id, 0,
                "R10_DUPLICATE", context.slots(), List.of(), List.of(), List.of(), "AI07_UNCHANGED",
                provenance(), new ContextIntelligenceService.Disposition(true, false, false));
        ObjectNode valid = json.valueToTree(view);
        var schema = schema("ticket-enrichment-v0.1.schema.json");
        assertTrue(schema.validate(valid).isEmpty());

        var authority = valid.deepCopy();
        ((ObjectNode) authority.path("disposition")).put("operationalAuthority", true);
        assertFalse(schema.validate(authority).isEmpty());
        var unknown = valid.deepCopy();
        ((ObjectNode) unknown.path("context")).put("priority", "HIGH");
        assertFalse(schema.validate(unknown).isEmpty());
        var evidence = valid.deepCopy();
        ((ObjectNode) evidence.path("context").path("location.room").path("evidence").get(0)).put("sourceId", "client-arbitrary-key");
        assertFalse(schema.validate(evidence).isEmpty());
    }

    @Test
    void duplicateRejectsFakeConfidenceMergeAndMissingR10Support() throws Exception {
        var result = new DuplicateDecisionEngine.Result(
                DuplicateDecisionEngine.Decision.MATCH_READY_FOR_REVIEW,
                List.of("SAME_ROOM", "SAME_OBJECT_TYPE", "SAME_OBJECT", "SAME_CATEGORY", "PREDECESSOR_OPEN", "COMPATIBLE_SYMPTOM"),
                List.of(), List.of(), null, "NON_PROBABILISTIC_INTERNAL_ONLY");
        var view = new ContextIntelligenceService.DuplicateView(
                ContextIntelligenceService.DUPLICATE_VERSION, "REVIEW_REQUIRED", "ASSISTED", UUID.randomUUID(), 0,
                result.decision(), null, List.of(new ContextIntelligenceService.CandidateView(UUID.randomUUID(), null, 0, result)),
                1, false, provenance(), new ContextIntelligenceService.Disposition(true, false, false));
        ObjectNode valid = json.valueToTree(view);
        var schema = schema("ticket-duplicate-probe-v0.1.schema.json");
        assertTrue(schema.validate(valid).isEmpty());
        var confidence = valid.deepCopy();
        ((ObjectNode) confidence.path("candidates").get(0).path("assessment")).put("confidence", 0.99);
        assertFalse(schema.validate(confidence).isEmpty());
        var merge = valid.deepCopy();
        merge.put("duplicateOf", UUID.randomUUID().toString());
        assertFalse(schema.validate(merge).isEmpty());
        var missing = valid.deepCopy();
        ((com.fasterxml.jackson.databind.node.ArrayNode) missing.path("candidates").get(0).path("assessment").path("matched"))
                .remove(3);
        assertFalse(schema.validate(missing).isEmpty());
    }

    @Test
    void snapshotRejectsFutureRevisionDuplicateSourceAndClientControlledFieldKeys() {
        var id = UUID.randomUUID();
        var future = new ContextSnapshot.Source("clarification:00000000-0000-0000-0000-000000000001:0", 1, "15", "device.identifier", false);
        assertThrows(IllegalArgumentException.class, () -> new ContextSnapshot("lab", id, 0, List.of(future), "test"));
        var source = new ContextSnapshot.Source("title", 0, "text", null, false);
        assertThrows(IllegalArgumentException.class, () -> new ContextSnapshot("lab", id, 0, List.of(source, source), "test"));
        var arbitrary = new ContextSnapshot.Source("client-field", 0, "text", null, false);
        assertThrows(IllegalArgumentException.class, () -> new ContextSnapshot("lab", id, 0, List.of(arbitrary), "test"));
    }

    private ContextIntelligenceService.Provenance provenance() {
        return new ContextIntelligenceService.Provenance("a".repeat(64), policy.bundleDigest(), policy.digests(),
                null, "bounded-rule-first-cpu@0.1.0", null);
    }

    private JsonSchema schema(String name) throws Exception {
        try (var stream = getClass().getResourceAsStream("/servicedesk-contract/" + name)) {
            assertNotNull(stream);
            JsonNode node = json.readTree(stream);
            return JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012).getSchema(node);
        }
    }
}
