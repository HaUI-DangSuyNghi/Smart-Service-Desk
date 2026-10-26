package vn.haui.dx.apps.servicedesk.intelligence;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import vn.haui.dx.apps.servicedesk.intelligence.assessment.AssessmentPolicy;
import vn.haui.dx.apps.servicedesk.intelligence.assessment.ProblemAssessor;
import vn.haui.dx.apps.servicedesk.intelligence.taxonomy.SubcategoryResolver;
import vn.haui.dx.apps.servicedesk.intelligence.taxonomy.TaxonomyCatalog;

import java.util.List;
import java.util.Map;

class HierarchicalPolicyTest {
    private final ObjectMapper json = new ObjectMapper();
    private final AssessmentPolicy policy = new AssessmentPolicy(json);
    private final SubcategoryResolver resolver = new SubcategoryResolver(new TaxonomyCatalog(json));
    private final ProblemAssessor assessor = new ProblemAssessor(policy);

    @ParameterizedTest
    @CsvSource({
        "I1,U1,LOW",
        "I1,U2,LOW",
        "I1,U3,MEDIUM",
        "I1,U4,HIGH",
        "I2,U1,LOW",
        "I2,U2,MEDIUM",
        "I2,U3,HIGH",
        "I2,U4,HIGH",
        "I3,U1,MEDIUM",
        "I3,U2,HIGH",
        "I3,U3,HIGH",
        "I3,U4,CRITICAL",
        "I4,U1,HIGH",
        "I4,U2,HIGH",
        "I4,U3,CRITICAL",
        "I4,U4,CRITICAL"
    })
    void exactBusinessMatrix(String impact, String urgency, String expected) {
        assertEquals(expected, policy.priority(impact, urgency));
    }

    @Test
    void unknownAxesNeverDefault() {
        assertNull(policy.priority(null, "U4"));
        assertNull(policy.priority("I4", null));
        var facts = assessor.assess(Map.of("title", "Wi-Fi hỏng", "description", ""));
        assertNull(facts.impact().value());
        assertNull(facts.urgency().value());
        assertNull(facts.priority());
        assertEquals("NOT_ASSESSED", facts.risk().status());
        assertNull(facts.risk().level());
    }

    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {
                "NETWORK|không kết nối được Wi-Fi|WIFI_CONNECTION|SUPPORTED_BY_RULE",
                "NETWORK|Wi-Fi|NULL|INSUFFICIENT_EVIDENCE",
                "NETWORK|không kết nối được Wi-Fi và mất mạng dây|NULL|AMBIGUOUS",
                "HARDWARE|không kết nối được Wi-Fi|NULL|TAXONOMY_CONFLICT",
                "NETWORK|không phải mất Wi-Fi|NULL|INSUFFICIENT_EVIDENCE",
                "NETWORK|ví dụ mất Wi-Fi|NULL|INSUFFICIENT_EVIDENCE",
                "SOFTWARE|triệu chứng chưa biết|NULL|INSUFFICIENT_EVIDENCE",
                "OTHER|request a room|NULL|INSUFFICIENT_EVIDENCE"
            })
    void explicitSymptomsRespectParentAndAbstention(
            String parent, String text, String child, String status) {
        var result = resolver.resolve(parent, Map.of("title", text));
        assertEquals("NULL".equals(child) ? null : child, result.child());
        assertEquals(status, result.status());
        assertEquals(result, resolver.resolve(parent, Map.of("title", text)));
    }

    @Test
    void unicodeOffsetsAndIndependentAxes() {
        var fields = Map.of("title", "🚀 cả phòng thi mất Wi-Fi", "description", "thi sau 15 phút");
        var child = resolver.resolve("NETWORK", fields);
        var facts = assessor.assess(fields);
        assertEquals("WIFI_CONNECTION", child.child());
        assertEquals("I3", facts.impact().value());
        assertEquals("U4", facts.urgency().value());
        assertEquals("CRITICAL", facts.priority());
        for (var span : child.evidence()) {
            assertTrue(span.verifies(fields.get(span.field())));
        }
        for (var span : facts.evidence()) {
            assertTrue(span.verifies(fields.get(span.field())));
        }
        String title = fields.get("title");
        assertEquals(
                title.codePointCount(0, title.indexOf("mất Wi-Fi")),
                child.evidence().get(0).start());
        assertNull(resolver.resolve(null, fields).child());
    }

    @Test
    void contradictoryFactsAbstainAndRiskDoesNotOverridePriority() {
        var facts =
                assessor.assess(
                        Map.of(
                                "title",
                                "một máy bị ảnh hưởng; nhiều phòng bị ảnh hưởng",
                                "description",
                                "có thể chờ; không thể làm việc; thiết bị rò điện"));
        assertNull(facts.impact().value());
        assertEquals("CONFLICT", facts.impact().status());
        assertNull(facts.urgency().value());
        assertEquals("CONFLICT", facts.urgency().status());
        assertNull(facts.priority());
        assertEquals(List.of("SAFETY"), facts.risk().flags());
        assertNull(facts.risk().level());
        assertTrue(facts.risk().reviewRequired());
    }

    @ParameterizedTest
    @CsvSource({
        "title,một máy bị ảnh hưởng,I1",
        "description,nhiều máy bị ảnh hưởng,I2",
        "title,cả phòng bị ảnh hưởng,I3",
        "description,nhiều phòng bị ảnh hưởng,I4"
    })
    void impactNeedsExplicitScope(String field, String text, String expected) {
        assertEquals(expected, assessor.assess(Map.of(field, text)).impact().value());
    }

    @ParameterizedTest
    @CsvSource({
        "có phương án thay thế,U1",
        "có thể chờ,U2",
        "không thể làm việc,U3",
        "thi sau 15 phút,U4"
    })
    void urgencyNeedsOperationalEvidence(String text, String expected) {
        assertEquals(expected, assessor.assess(Map.of("description", text)).urgency().value());
    }

    @Test
    void invalidTaxonomyIsRejectedAtStartup() throws Exception {
        var original = json.readTree(AssessmentPolicy.read("taxonomy-v0.1.json"));
        for (String mutation : List.of("duplicate", "parent", "cycle", "version", "evidence")) {
            var root = original.deepCopy();
            ObjectNode child = (ObjectNode) root.path("children").get(0);
            switch (mutation) {
                case "duplicate" ->
                        ((ObjectNode) root.path("children").get(1))
                                .put("id", child.path("id").asText());
                case "parent" -> child.put("parent", "UNKNOWN");
                case "cycle" -> child.put("parent", child.path("id").asText());
                case "version" -> child.put("version", "old");
                default -> child.putArray("requiredEvidence");
            }
            assertThrows(IllegalArgumentException.class, () -> new TaxonomyCatalog(root), mutation);
        }
        policy.verifyUnchanged();
        assertTrue(policy.bundleDigest().matches("[0-9a-f]{64}"));
    }
}
