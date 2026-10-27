package vn.haui.dx.apps.servicedesk.intelligence.duplicate;

import static org.junit.jupiter.api.Assertions.*;
import static vn.haui.dx.apps.servicedesk.intelligence.duplicate.DuplicateDecisionEngine.Decision.*;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;

import vn.haui.dx.apps.servicedesk.intelligence.assessment.AssessmentPolicy;
import vn.haui.dx.apps.servicedesk.intelligence.context.ContextPolicyCatalog;
import vn.haui.dx.apps.servicedesk.intelligence.context.ContextSlotExtractor;
import vn.haui.dx.apps.servicedesk.intelligence.context.ContextSnapshot;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

class DuplicateDecisionEngineTest {
    private final ObjectMapper json = new ObjectMapper();
    private final ContextSlotExtractor extractor = new ContextSlotExtractor(
            new ContextPolicyCatalog(json), new AssessmentPolicy(json));
    private final DuplicateDecisionEngine engine = new DuplicateDecisionEngine();

    @Test
    void fourR10ConditionsAndSymptomSupportAreRequired() {
        var query = input("Máy 15 phòng 1304 không lên nguồn", "HARDWARE", true, true);
        var candidate = input("PC số 15 ở 1304 bật không lên", "HARDWARE", true, true);
        var result = engine.decide(query, candidate);
        assertEquals(MATCH_READY_FOR_REVIEW, result.decision());
        assertTrue(result.missing().isEmpty());
        assertTrue(result.conflicts().isEmpty());
        assertNull(result.confidence());
        assertEquals("NON_PROBABILISTIC_INTERNAL_ONLY", result.rankScoreSemantics());
    }

    @Test
    void differentRoomDeviceCategorySymptomOrClosedTicketBlocksMatch() {
        var query = input("Máy 15 phòng 1304 không lên nguồn", "HARDWARE", true, true);
        var hardNegatives = List.of(
                input("Máy 16 phòng 1304 không lên nguồn", "HARDWARE", true, true),
                input("Máy 15 phòng 1305 không lên nguồn", "HARDWARE", true, true),
                input("Máy 15 phòng 1304 không lên nguồn", "NETWORK", true, true),
                input("Máy 15 phòng 1304 mất mạng", "HARDWARE", true, true),
                input("Máy 15 phòng 1304 không lên nguồn", "HARDWARE", true, false));
        for (var candidate : hardNegatives) {
            assertEquals(DISTINCT, engine.decide(query, candidate).decision());
        }
    }

    @Test
    void classifierProposalIsNotAnApprovedCategory() {
        var query = input("Máy 15 phòng 1304 không lên nguồn", "HARDWARE", false, true);
        var candidate = input("Máy 15 phòng 1304 không lên nguồn", "HARDWARE", false, true);
        var result = engine.decide(query, candidate);
        assertEquals(POSSIBLE_DUPLICATE, result.decision());
        assertTrue(result.missing().contains("VERIFIED_CATEGORY"));
    }

    @Test
    void missingDeviceNeverBecomesExactDuplicate() {
        var query = input("Máy tính phòng 1304 không lên nguồn", "HARDWARE", true, true);
        var candidate = input("Máy 15 phòng 1304 không lên nguồn", "HARDWARE", true, true);
        assertEquals(POSSIBLE_DUPLICATE, engine.decide(query, candidate).decision());
    }

    @Test
    void multiRoomReportProducesSeparateRelatedSignal() {
        var query = input("Phòng 1304 và phòng 1305 mất mạng", "NETWORK", true, true);
        var candidate = input("Wi-Fi phòng 1304 mất mạng", "NETWORK", true, true);
        assertEquals(RELATED_INCIDENT_POSSIBLE, engine.decide(query, candidate).decision());
    }

    @Test
    void sameAuthorizedIdUsesIdentityAndDifferentWorkspaceNeverMatches() {
        var query = input("Máy 15 phòng 1304 không lên nguồn", "HARDWARE", true, true);
        assertEquals(SAME_TICKET_ID, engine.decide(query, query).decision());
        var foreign = new DuplicateDecisionEngine.Input("foreign", query.ticketId(), query.createdAt(),
                query.context(), query.category(), true, true);
        assertEquals(INSUFFICIENT_EVIDENCE, engine.decide(query, foreign).decision());
    }

    @Test
    void completeAbsenceOfFactsAbstains() {
        var query = input("Cần hỗ trợ", null, false, true);
        var candidate = input("Có lỗi", null, false, true);
        assertEquals(INSUFFICIENT_EVIDENCE, engine.decide(query, candidate).decision());
    }

    private DuplicateDecisionEngine.Input input(String text, String category, boolean verified, boolean open) {
        var id = UUID.randomUUID();
        var context = extractor.extract(new ContextSnapshot("lab", id, 0,
                List.of(new ContextSnapshot.Source("title", 0, text, null, false)), "test"));
        return new DuplicateDecisionEngine.Input("lab", id, Instant.parse("2026-10-26T00:00:00Z"),
                context, category, verified, open);
    }
}
