package vn.haui.dx.apps.servicedesk.intelligence;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import vn.haui.dx.apps.servicedesk.Ticket;
import vn.haui.dx.apps.servicedesk.intelligence.assessment.AssessmentPolicy;
import vn.haui.dx.hpdi.intelligence.api.IntelligenceFailure;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BackgroundModelAssessmentTest {
    private final TicketClassificationOrchestrator classifier = mock(TicketClassificationOrchestrator.class);
    private final BackgroundModelAssessment service;

    BackgroundModelAssessmentTest() {
        var json = new ObjectMapper();
        when(classifier.modelDigest()).thenReturn("a".repeat(64));
        service = new BackgroundModelAssessment(LocalModelRuntime.external(), classifier,
                mock(ClassifierProcessingGate.class), new AssessmentPolicy(json), json);
    }

    private Ticket ticket(String title, String description) {
        return new Ticket(UUID.randomUUID(), "lab-a", "owner", UUID.randomUUID(), title, description, Instant.now(), null);
    }

    private TicketClassificationView model(String category, String status, List<String> reasons) {
        return new TicketClassificationView("model-primary-assessment@0.1.0", "ASSISTED", status,
                new TicketClassificationView.Category(category == null ? "ABSTAIN" : "PROPOSE", category, "QWEN", "a".repeat(64), "b".repeat(64), null, "NOT_AVAILABLE", "UNVERIFIED_BY_MODEL"),
                new TicketClassificationView.Disposition("REVIEW_REQUIRED", reasons, List.of(), false),
                new TicketClassificationView.IndependentTriage(null, null, null, "NOT_ASSESSED"), true, false);
    }

    @Test void numberedComputerAndImminentExamRetainIndependentEvidence() {
        var ticket = ticket("Máy 15 phòng 304 A1 không lên nguồn", "Máy này tôi đang cần để làm bài thi gấp trong 15 phút nữa");
        var result = service.compose(ticket, model("HARDWARE", "REVIEW_REQUIRED", List.of()));
        assertEquals("model", result.engine());
        assertEquals("HARDWARE", result.category());
        assertEquals("I1", result.impact());
        assertEquals("U4", result.urgency());
        assertEquals("HIGH", result.priority());
        assertTrue(result.needsReview());
        assertTrue(result.nextQuestions().isEmpty());
        assertNotNull(result.explanation());
        for (var span : result.evidence()) assertTrue(("title".equals(span.field()) ? ticket.title() : ticket.description()).contains(span.text()));
    }

    @Test void missingImpactDoesNotEraseModelCategoryOrInventMediumPriority() {
        var result = service.compose(ticket("Máy không lên nguồn", "Xin hỗ trợ"), model("HARDWARE", "REVIEW_REQUIRED", List.of()));
        assertEquals("HARDWARE", result.category());
        assertNull(result.priority());
        assertEquals("MODEL_CLASSIFIED", result.reasonCode());
        assertEquals(2, result.nextQuestions().size());
    }

    @Test void hypotheticalsAndAlreadyRepairedDevicesDoNotGetPriority() {
        for (String text : List.of("Nếu máy 15 không lên nguồn", "Máy 15 không lên nguồn đã khắc phục", "Máy 15 không bị hỏng")) {
            var result = service.compose(ticket(text, "Xin hỗ trợ"), model("HARDWARE", "REVIEW_REQUIRED", List.of()));
            assertNull(result.impact());
            assertNull(result.priority());
        }
    }

    @Test void abstentionSafetyAndOutagesRemainDistinct() {
        var ticket = ticket("Máy 15 không lên nguồn", "thi sau 10 phút");
        var abstained = service.compose(ticket, model(null, "REVIEW_REQUIRED", List.of("MODEL_ABSTAINED")));
        assertNull(abstained.category());
        assertEquals("MODEL_ABSTAINED", abstained.reasonCode());
        var unsafe = service.compose(ticket, model("HARDWARE", "REVIEW_REQUIRED", List.of("SAFETY_GATE")));
        assertNull(unsafe.category());
        assertNull(unsafe.priority());
        assertTrue(unsafe.evidence().isEmpty());
        assertEquals("MODEL_UNAVAILABLE", service.compose(ticket, model(null, "DEGRADED", List.of("INTELLIGENCE_UNAVAILABLE"))).reasonCode());
    }

    private TicketIntelligenceView.Triage rules(String category) {
        return new TicketIntelligenceView.Triage("SUCCESS", category, "HIGH", "IT-HARDWARE", "I1", "U4", "CLASSIFIED",
                List.of("CAT-HW-001"), List.of(new TicketIntelligenceView.InputEvidence("title", "không lên nguồn")), false, null);
    }

    @Test void modelAndRuleEngineRemainIndependentAndBothAreStored() {
        var ticket = ticket("Máy 15 không lên nguồn", "thi sau 10 phút");
        var agree = service.cooperate(service.compose(ticket, model("HARDWARE", "REVIEW_REQUIRED", List.of())), rules("HARDWARE"));
        assertEquals("hybrid", agree.engine());
        assertEquals("AGREEMENT", agree.cooperation().decision());
        assertEquals("HARDWARE", agree.cooperation().rulesCategory());
        var conflict = service.cooperate(service.compose(ticket, model("NETWORK", "REVIEW_REQUIRED", List.of())), rules("HARDWARE"));
        assertNull(conflict.category());
        assertEquals("HYBRID_CONFLICT", conflict.reasonCode());
        assertEquals("NETWORK", conflict.cooperation().modelCategory());
        assertEquals("HARDWARE", conflict.cooperation().rulesCategory());
        assertTrue(conflict.needsReview());
    }

    @Test void modelOutageDoesNotDisableRulesAndFallbackIsExplicit() {
        var ticket = ticket("Máy 15 không lên nguồn", "thi sau 10 phút");
        var result = service.cooperate(service.compose(ticket, model(null, "DEGRADED", List.of("INTELLIGENCE_UNAVAILABLE"))), rules("HARDWARE"));
        assertEquals("HARDWARE", result.category());
        assertEquals("HIGH", result.priority());
        assertEquals("UNAVAILABLE", result.cooperation().modelStatus());
        assertEquals("CLASSIFIED", result.cooperation().rulesStatus());
        assertEquals("RULES_SUPPORTED", result.cooperation().decision());
    }
}
