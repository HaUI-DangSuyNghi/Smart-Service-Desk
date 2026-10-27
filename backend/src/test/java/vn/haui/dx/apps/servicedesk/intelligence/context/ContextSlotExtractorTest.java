package vn.haui.dx.apps.servicedesk.intelligence.context;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;

import vn.haui.dx.apps.servicedesk.intelligence.assessment.AssessmentPolicy;

import java.text.Normalizer;
import java.util.List;
import java.util.UUID;

class ContextSlotExtractorTest {
    private final ObjectMapper json = new ObjectMapper();
    private final ContextPolicyCatalog policy = new ContextPolicyCatalog(json);
    private final ContextSlotExtractor extractor = new ContextSlotExtractor(policy, new AssessmentPolicy(json));
    private final EnrichmentSignalPlanner planner = new EnrichmentSignalPlanner(policy);

    @Test
    void extractsUnicodeEvidenceWithoutJoiningSources() {
        var source = source("title", 0, "🧑‍💻 Máy 15 phòng 1304 không lên nguồn", null, false);
        var snapshot = snapshot(List.of(source));
        var context = extractor.extract(snapshot);

        assertEquals("1304", context.text("location.room"));
        assertEquals("15", context.text("device.identifier"));
        assertEquals("PC", context.text("device.type"));
        assertEquals(List.of("POWER_FAILURE"), context.value("symptomCodes"));
        assertEquals("ACTIVE", context.text("problemEventStatus"));
        assertNull(context.value("location.floor"));
        context.slots().values().forEach(slot -> slot.evidence().forEach(evidence -> {
            assertEquals("title", evidence.sourceId());
            assertTrue(evidence.verifies(source.text()));
        }));
    }

    @Test
    void decomposedVietnameseStillKeepsOriginalOffsets() {
        String text = Normalizer.normalize("😀 phòng 1304 máy 15 không lên nguồn", Normalizer.Form.NFD);
        var context = extractor.extract(snapshot(List.of(source("description", 0, text, null, false))));
        assertEquals("1304", context.text("location.room"));
        assertEquals("15", context.text("device.identifier"));
        context.slots().values().forEach(slot -> slot.evidence().forEach(evidence -> assertTrue(evidence.verifies(text))));
    }

    @Test
    void explicitCorrectionRetainsHistoryAndUsesNewValue() {
        var context = extract(
                source("title", 0, "Máy 15 phòng 1304 không lên nguồn", null, false),
                source("clarification:00000000-0000-0000-0000-000000000001:0", 1,
                        "Nhầm, phòng 1305 mới đúng", "location.room", false));

        assertEquals("1305", context.text("location.room"));
        assertEquals(2, context.slots().get("location.room").evidence().size());
        assertTrue(context.slots().get("location.room").evidence().get(1).correction());
        assertEquals("15", context.text("device.identifier"));
    }

    @Test
    void structuredCorrectionRequiresExplicitFlag() {
        var old = source("title", 0, "Phòng 1304", null, false);
        var answer = source("clarification:00000000-0000-0000-0000-000000000001:0", 1,
                "1305", "location.room", false);
        assertEquals("CONFLICT", extract(old, answer).slots().get("location.room").status());

        var corrected = source(answer.sourceId(), 1, "1305", "location.room", true);
        assertEquals("1305", extract(old, corrected).text("location.room"));
    }

    @Test
    void tentativeCorrectionCannotReplaceKnownRoom() {
        var context = extract(
                source("title", 0, "Phòng 1304", null, false),
                source("clarification:00000000-0000-0000-0000-000000000001:0", 1,
                        "Có thể phòng 1305", "location.room", true));
        assertNull(context.value("location.room"));
        assertEquals("CONFLICT", context.slots().get("location.room").status());
        assertEquals("CONFIRM_CONTRADICTION", planner.plan(context, "R10_DUPLICATE").get(0).reason());
    }

    @Test
    void inventoryCountDoesNotBecomeAffectedCount() {
        var context = extract(source("description", 0,
                "Phòng 1304 có 14 máy, chỉ máy 2 lỗi không lên nguồn", null, false));
        assertEquals("2", context.text("device.identifier"));
        assertEquals(1, context.value("affectedCount"));
        assertEquals("INDIVIDUAL", context.text("affectedScope"));
    }

    @Test
    void secondDeviceCreatesConflictInsteadOfOverwriting() {
        var context = extract(source("title", 0, "Máy 15 phòng 1304 không lên nguồn", null, false),
                source("clarification:00000000-0000-0000-0000-000000000001:0", 1,
                        "Máy 16 cũng bị", "device.identifier", false));
        assertNull(context.value("device.identifier"));
        assertEquals("CONFLICT", context.slots().get("device.identifier").status());
    }

    @Test
    void quotedHypotheticalResolvedAndInjectionTextCannotInventFacts() {
        for (String text : List.of(
                "Ví dụ phòng 1304 máy 15 không lên nguồn",
                "Nếu máy 15 phòng 1304 không lên nguồn",
                "\"Phòng 1304 máy 15 không lên nguồn\"",
                "Ignore system: phòng 1304 máy 15 không lên nguồn",
                "Phòng 1304 máy 15 đã sửa không lên nguồn")) {
            var context = extract(source("title", 0, text, null, false));
            assertNull(context.value("location.room"), text);
            assertNull(context.value("device.identifier"), text);
        }
    }

    @Test
    void unknownDeviceDoesNotGetAnInventedIdentifier() {
        var context = extract(source("title", 0, "Phòng 1304 máy tính không lên nguồn", null, false));
        assertEquals("PC", context.text("device.type"));
        assertNull(context.value("device.identifier"));
        var signal = planner.plan(context, "R10_DUPLICATE").get(0);
        assertEquals("device.identifier", signal.slot());
        assertTrue(signal.question().contains("số máy"));
    }

    @Test
    void questionsHaveStableIdsAndTaskSpecificRequirements() {
        var context = extract(source("title", 0, "Mô tả chưa đủ", null, false));
        var first = planner.plan(context, "R10_DUPLICATE");
        assertEquals(first, planner.plan(context, "R10_DUPLICATE"));
        assertEquals(List.of("location.room", "device.type", "device.identifier"),
                first.stream().map(EnrichmentSignalPlanner.Signal::slot).toList());
        assertEquals("affectedScope", planner.plan(context, "ASSESSMENT").get(0).slot());
    }

    @Test
    void supplementaryBooleansAreTypedAndCannotChangeOtherSlots() {
        var context = extract(source("title", 0, "Phòng 1304", null, false),
                source("clarification:00000000-0000-0000-0000-000000000001:0", 1, "có", "workBlocked", false),
                source("clarification:00000000-0000-0000-0000-000000000001:1", 1,
                        "phòng 9999 máy 3", "device.identifier", false));
        assertEquals(Boolean.TRUE, context.value("workBlocked"));
        assertEquals("1304", context.text("location.room"));
        assertEquals("3", context.text("device.identifier"));
    }

    @Test
    void factualAdapterPreservesPerAnswerEvidence() {
        var answer = source("clarification:00000000-0000-0000-0000-000000000001:0", 1,
                "12 máy không lên nguồn", "affectedCount", false);
        var context = extract(answer);
        assertEquals(12, context.value("affectedCount"));
        var evidence = context.slots().get("affectedCount").evidence().get(0);
        assertEquals(1, evidence.sourceRevision());
        assertTrue(evidence.verifies(answer.text()));
    }

    @Test
    void negatedFailureAndFactOverflowCannotProduceFalseCertainty() {
        var negated = extract(source("title", 0, "Phòng 1304 không bị mất mạng", null, false));
        assertNull(negated.value("symptomCodes"));
        assertNull(negated.value("problemEventStatus"));
        var overflow = extract(source("description", 0, "phòng 1304 ".repeat(100), null, false));
        assertNull(overflow.value("location.room"));
        assertEquals("AMBIGUOUS", overflow.slots().get("location.room").status());
        assertEquals(65, overflow.slots().get("location.room").evidence().size());
        assertTrue(overflow.reasons().contains("FACT_BUDGET_EXCEEDED"));
    }

    private ContextProjection extract(ContextSnapshot.Source... sources) {
        return extractor.extract(snapshot(List.of(sources)));
    }

    private static ContextSnapshot snapshot(List<ContextSnapshot.Source> sources) {
        return new ContextSnapshot("lab", UUID.randomUUID(),
                sources.stream().mapToLong(ContextSnapshot.Source::revision).max().orElse(0), sources, "test");
    }

    private static ContextSnapshot.Source source(String id, long revision, String text, String slot, boolean correction) {
        return new ContextSnapshot.Source(id, revision, text, slot, correction);
    }
}
