package vn.haui.dx.apps.servicedesk.intelligence;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import vn.haui.dx.apps.servicedesk.intelligence.assessment.AssessmentPolicy;
import vn.haui.dx.apps.servicedesk.intelligence.assessment.ProblemAssessor;
import vn.haui.dx.apps.servicedesk.intelligence.context.*;
import vn.haui.dx.hpdi.common.api.CapabilityException;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class TicketAssessmentInputAssemblerTest {
    private final ObjectMapper json = new ObjectMapper();
    private final AssessmentPolicy assessment = new AssessmentPolicy(json);
    private final ContextSlotExtractor extractor = new ContextSlotExtractor(new ContextPolicyCatalog(json), assessment);
    private final TicketAssessmentInputAssembler inputs = new TicketAssessmentInputAssembler(json, assessment,
            new ContextPolicyCatalog(json), new ClassifierConfiguration.Settings(ClassifierConfiguration.Mode.DISABLED, "", "", "", "", ""));
    private final ContextualFacts facts = new ContextualFacts(new ProblemAssessor(assessment), assessment, inputs);

    @Test
    void sourceMapVerifiesEmojiAndRejectsGeneratedDelimiter() {
        var snapshot = snapshot("😀 Máy tính lỗi", List.of());
        var input = inputs.assemble(snapshot, extractor.extract(snapshot));
        var mapped = inputs.mapSpan(input, snapshot, "title", 2, 5);
        assertEquals("Máy", mapped.quote());
        assertTrue(mapped.verifies(snapshot.sources().get(0).text()));
        assertThrows(CapabilityException.class, () -> inputs.mapSpan(input, snapshot, "description", 0, 1));
    }

    @Test
    void roomOnlyAnswerDoesNotAmplifyCoarseClassificationCalls() {
        var original = snapshot("Máy tính không lên nguồn", List.of());
        var changed = snapshot("Máy tính không lên nguồn", List.of(answer(1, "location.room", "1304", false)));
        assertEquals(inputs.assemble(original, extractor.extract(original)).inputDigest(),
                inputs.assemble(changed, extractor.extract(changed)).inputDigest());
    }

    @Test
    void typedAnswersUpdateFactsWithoutLosingOriginalOffsets() {
        var snapshot = snapshot("Máy tính lỗi", List.of(answer(1, "affectedScope", "WHOLE_ROOM", false),
                new ContextSnapshot.Source("clarification:00000000-0000-0000-0000-000000000001:1", 1, "có", "workBlocked", false)));
        var result = facts.assess(snapshot, extractor.extract(snapshot));
        assertEquals("I3", result.impact());
        assertEquals("U3", result.urgency());
        assertNotNull(result.priority());
        for (var evidence : result.evidence()) {
            var source = snapshot.sources().stream().filter(item -> item.sourceId().equals(evidence.sourceId())).findFirst().orElseThrow();
            assertTrue(evidence.verifies(source.text()));
        }
    }

    @Test
    void correctionDoesNotReuseHistoricalScopeOrUrgency() {
        var snapshot = snapshot("Cả phòng mất mạng, không thể làm việc", List.of(
                answer(1, "affectedCount", "1", true),
                new ContextSnapshot.Source("clarification:00000000-0000-0000-0000-000000000001:1", 1, "không", "workBlocked", true)));
        var result = facts.assess(snapshot, extractor.extract(snapshot));
        assertEquals("I1", result.impact());
        assertNull(result.urgency());
        assertNull(result.priority());
    }

    @Test
    void answerToRoomQuestionCannotInventImpact() {
        var snapshot = snapshot("Máy tính lỗi", List.of(answer(1, "location.room", "Cả phòng mất mạng", false)));
        assertNull(facts.assess(snapshot, extractor.extract(snapshot)).impact());
    }

    private ContextSnapshot snapshot(String title, List<ContextSnapshot.Source> answers) {
        var sources = new java.util.ArrayList<ContextSnapshot.Source>();
        sources.add(new ContextSnapshot.Source("title", 0, title, null, false));
        sources.add(new ContextSnapshot.Source("description", 0, "", null, false));
        sources.addAll(answers);
        return new ContextSnapshot("lab", UUID.randomUUID(), answers.isEmpty() ? 0 : 1, sources, "a".repeat(64));
    }

    private ContextSnapshot.Source answer(long revision, String slot, String value, boolean correction) {
        return new ContextSnapshot.Source("clarification:00000000-0000-0000-0000-000000000001:0", revision, value, slot, correction);
    }
}
