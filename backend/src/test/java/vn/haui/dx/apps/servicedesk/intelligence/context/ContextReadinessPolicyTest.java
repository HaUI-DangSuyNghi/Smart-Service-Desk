package vn.haui.dx.apps.servicedesk.intelligence.context;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import vn.haui.dx.apps.servicedesk.intelligence.assessment.AssessmentPolicy;
import vn.haui.dx.apps.servicedesk.intelligence.assessment.ProblemAssessor;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static vn.haui.dx.apps.servicedesk.intelligence.context.ContextReadinessView.State.*;

class ContextReadinessPolicyTest {
    private final ObjectMapper json = new ObjectMapper();
    private final AssessmentPolicy assessment = new AssessmentPolicy(json);
    private final ContextReadinessPolicy readiness = new ContextReadinessPolicy(json, new ProblemAssessor(assessment));
    private final ContextSlotExtractor extractor = new ContextSlotExtractor(new ContextPolicyCatalog(json), assessment);

    @Test
    void missingDeviceIdOnlyBlocksExactDuplicateTask() {
        var result = evaluate("Máy tính phòng 1304 không lên nguồn", 0);
        assertEquals(ENRICHMENT_OPTIONAL, result.overall());
        assertEquals(READINESS_OK, result.tasks().get(0).state());
        assertEquals(List.of("EXACT_DUPLICATE_ASSERTION"), result.tasks().get(1).blockedOperations());
        assertFalse(result.workflowGateApplied());
        assertFalse(result.operationalAuthority());
    }

    @Test
    void fullIdentityDoesNotCreateArtificialWait() {
        assertEquals(READINESS_OK, evaluate("Máy 15 phòng 1304 không lên nguồn", 0).overall());
    }

    @Test
    void personalAccountDoesNotRequireRoomOrDevice() {
        var result = evaluate("Tài khoản cá nhân không đăng nhập được", 0);
        assertEquals(READINESS_OK, result.tasks().get(0).state());
        assertTrue(result.questions().stream().noneMatch(question -> question.slot().equals("location.room")));
    }

    @Test
    void vagueComplaintAsksOnlyThreeUsefulQuestions() {
        var result = evaluate("Xin hỗ trợ, chưa rõ nguyên nhân", 0);
        assertEquals(ENRICHMENT_REQUIRED, result.overall());
        assertTrue(result.questions().size() <= 3);
        assertEquals("device.type", result.questions().get(0).slot());
    }

    @Test
    void criticalDeadlineAndDataLossNeverWaitForAnswers() {
        for (String text : List.of("Máy tính bị lỗi, thi sau 5 phút", "Máy tính: dữ liệu đã bị mất", "Thiết bị rò điện")) {
            var result = evaluate(text, 0);
            assertEquals(MANUAL_REVIEW_REQUIRED, result.overall(), text);
            assertTrue(result.questions().isEmpty());
            assertEquals("COORDINATOR_REVIEW", result.nextAction());
        }
    }

    @Test
    void twoUnresolvedRoundsStopAskLoop() {
        assertEquals(MANUAL_REVIEW_REQUIRED, evaluate("Chưa rõ lỗi", 2).overall());
        assertEquals(READINESS_OK, evaluate("Máy 15 phòng 1304 không lên nguồn", 2).overall());
    }

    @Test
    void conflictingRoomCannotBeSilentlyReplaced() {
        var snapshot = new ContextSnapshot("lab", UUID.randomUUID(), 1, List.of(
                new ContextSnapshot.Source("title", 0, "Máy 15 phòng 1304 không lên nguồn", null, false),
                new ContextSnapshot.Source("clarification:00000000-0000-0000-0000-000000000001:0", 1,
                        "1305", "location.room", false)), "a".repeat(64));
        var result = readiness.evaluate(snapshot, extractor.extract(snapshot), 0);
        assertEquals(ENRICHMENT_REQUIRED, result.overall());
        assertTrue(result.questions().get(0).correctionRequired());
        assertTrue(result.reasonCodes().contains("CONFLICTING_USER_REPORTS"));
    }

    private ContextReadinessView evaluate(String text, int rounds) {
        var snapshot = new ContextSnapshot("lab", UUID.randomUUID(), 0,
                List.of(new ContextSnapshot.Source("title", 0, text, null, false)), "a".repeat(64));
        return readiness.evaluate(snapshot, extractor.extract(snapshot), rounds);
    }
}
