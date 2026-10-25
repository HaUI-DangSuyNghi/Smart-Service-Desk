package vn.haui.dx.apps.servicedesk.intelligence;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import vn.haui.dx.apps.servicedesk.Ticket;
import vn.haui.dx.apps.servicedesk.TicketService;
import vn.haui.dx.apps.servicedesk.assistance.TicketAssistanceService;
import vn.haui.dx.hpdi.common.api.CapabilityException;
import vn.haui.dx.hpdi.human.api.Actor;
import vn.haui.dx.hpdi.human.api.HumanContext;
import vn.haui.dx.hpdi.intelligence.api.AssistanceOutcome;
import vn.haui.dx.hpdi.intelligence.api.IntelligenceCapability;
import vn.haui.dx.hpdi.intelligence.api.IntelligenceFailure;
import vn.haui.dx.hpdi.intelligence.api.TriageSuggestion;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TicketIntelligenceOrchestratorTest {
    private final ObjectMapper json = new ObjectMapper();
    private final TicketService tickets = mock(TicketService.class);
    private final IntelligenceCapability intelligence = mock(IntelligenceCapability.class);
    private final TicketAssistanceService assistance = mock(TicketAssistanceService.class);
    private final HumanContext human = mock(HumanContext.class);
    private final UUID id = UUID.randomUUID();
    private final Ticket ticket = new Ticket(
            id, "lab-a", "owner", UUID.randomUUID(), "wifi một máy", "có thể chờ",
            Instant.now(), null);
    private TicketIntelligenceOrchestrator assistant;

    @BeforeEach
    void setup() throws Exception {
        assistant = new TicketIntelligenceOrchestrator(tickets, intelligence, assistance, json, human);
        when(tickets.get("lab-a", id)).thenReturn(ticket);
        when(human.currentActor("lab-a"))
                .thenReturn(new Actor("owner", "lab-a", Set.of("requester"), Set.of()));
        when(assistance.availableFor("lab-a")).thenReturn(true);
        reply(success());
    }

    @Test
    void deterministicPathUsesPersistedInputAndNeverCallsGuidance() {
        var result = assess(false);

        assertEquals("COMPLETE", result.status());
        assertEquals("NETWORK", result.triage().category());
        assertEquals("LOW", result.triage().priority());
        assertTrue(result.triage().explanation().priorityReason().contains("I1"));
        assertEquals("NOT_ASSESSED", result.risk().status());
        assertNull(result.risk().level());
        assertEquals("NOT_REQUESTED", result.guidance().status());
        assertFalse(result.reviewRequired());
        verify(intelligence).suggest(eq("lab-a"), argThat(input ->
                input.title().equals(ticket.title()) && input.description().equals(ticket.description())
                        && input.profile().equals("service-desk") && input.version().equals("0.3.0")));
        verify(tickets, times(2)).get("lab-a", id);
        verify(assistance, never()).assist(anyString(), any(), anyString());
    }

    @Test
    void insufficientInformationPreservesFallbackAndAsksForDetails() throws Exception {
        var response = success();
        response.put("needsReview", true);
        ((ObjectNode) response.path("result"))
                .put("category", "OTHER").put("priority", "MEDIUM").put("suggestedGroup", "GENERAL-SUPPORT");
        var explanation = (ObjectNode) response.path("explanation");
        explanation.putNull("impact").putNull("urgency").put("reasonCode", "INSUFFICIENT_INFORMATION");
        explanation.putArray("matchedRules").add("CAT-OTHER-001").add("REVIEW-INSUFFICIENT-001");
        reply(response);

        var result = assess(false);

        assertEquals("REVIEW_REQUIRED", result.status());
        assertTrue(result.triage().needsReview());
        assertTrue(result.triage().explanation().priorityReason().contains("dự phòng"));
        assertEquals(2, result.nextQuestions().size());
    }

    @Test
    void guidanceMapsExistingExactQuoteSuccessAndStillRequiresHumanReview() {
        when(assistance.assist(eq("lab-a"), eq(id), anyString())).thenReturn(new AssistanceOutcome(
                "0.1.0", UUID.randomUUID().toString(), "SUCCESS", "Verify the device connection.",
                List.of(new AssistanceOutcome.Citation("C1", "Guide", "v1")), true, "GROUNDED"));

        var result = assess(true);

        assertEquals("COMPLETE", result.status());
        assertEquals("SUPPORTED", result.guidance().status());
        assertTrue(result.reviewRequired());
        assertEquals("C1", result.guidance().citations().get(0).citationId());
    }

    @Test
    void providerTimeoutKeepsTriageAndReauthorizesTicket() {
        when(assistance.assist(anyString(), any(), anyString())).thenThrow(IntelligenceFailure.timeout());

        var result = assess(true);

        assertEquals("PARTIAL", result.status());
        assertEquals("NETWORK", result.triage().category());
        assertEquals("UNAVAILABLE", result.guidance().status());
        assertNull(result.guidance().answer());
        verify(tickets, times(2)).get("lab-a", id);
    }

    @Test
    void unavailableTriageNeverInfersCategoryOrCallsGuidance() {
        when(intelligence.suggest(anyString(), any())).thenThrow(IntelligenceFailure.unavailable());

        var result = assess(true);

        assertEquals("DEGRADED", result.status());
        assertNull(result.triage().category());
        assertNull(result.triage().priority());
        assertTrue(result.reviewRequired());
        verifyNoInteractions(assistance);
    }

    @Test
    void unauthorizedTicketNeverCallsIntelligence() {
        when(tickets.get("lab-a", id)).thenThrow(CapabilityException.notFound("Ticket not found"));

        assertThrows(CapabilityException.class, () -> assess(true));
        verifyNoInteractions(intelligence, assistance, human);
    }

    @Test
    void sourceRevocationAndMalformedGuidanceFailTheWholeRequest() {
        when(assistance.assist(anyString(), any(), anyString()))
                .thenThrow(CapabilityException.forbidden("Knowledge denied"));
        assertThrows(CapabilityException.class, () -> assess(true));

        reset(assistance);
        when(assistance.availableFor("lab-a")).thenReturn(true);
        when(assistance.assist(anyString(), any(), anyString()))
                .thenThrow(IntelligenceFailure.invalid());
        assertThrows(IntelligenceFailure.class, () -> assess(true));
    }

    @Test
    void changedTicketOrRevokedActorSuppressesEvenTriageOnlyResults() {
        var changed = new Ticket(id, "lab-a", "owner", ticket.clientRequestId(),
                "new text", ticket.description(), ticket.createdAt(), null);
        when(tickets.get("lab-a", id)).thenReturn(ticket, changed);
        assertEquals(CapabilityException.Code.CONFLICT,
                assertThrows(CapabilityException.class, () -> assess(false)).code());

        when(tickets.get("lab-a", id)).thenReturn(ticket);
        when(human.currentActor("lab-a")).thenReturn(
                new Actor("owner", "lab-a", Set.of("requester"), Set.of()),
                new Actor("different", "lab-a", Set.of("agent"), Set.of()));
        assertEquals(CapabilityException.Code.FORBIDDEN,
                assertThrows(CapabilityException.class, () -> assess(false)).code());
    }

    @ParameterizedTest
    @ValueSource(strings = {"workspace", "category", "priority", "rule", "evidence", "profile", "engine"})
    void rejectsTamperedDomainProjection(String mutation) throws Exception {
        var response = success();

        switch (mutation) {
            case "workspace" -> response.put("workspaceId", "foreign");
            case "category" -> ((ObjectNode) response.path("result")).put("category", "ALIEN");
            case "priority" -> ((ObjectNode) response.path("result")).put("priority", "CRITICAL");
            case "rule" -> ((ObjectNode) response.path("explanation")).putArray("matchedRules").add("UNKNOWN");
            case "evidence" -> ((ObjectNode) response.path("evidence").get(0)).put("text", "invented");
            case "profile" -> ((ObjectNode) response.path("runtime")).put("profileVersion", "service-desk@0.2.0");
            case "engine" -> ((ObjectNode) response.path("runtime")).put("engine", "model");
            default -> fail("Unknown mutation");
        }

        reply(response);
        assertThrows(IntelligenceFailure.class, () -> assess(false));
        verifyNoInteractions(assistance);
    }

    private TicketIntelligenceView assess(boolean guidance) {
        return assistant.assess("lab-a", id, new TicketAssessmentRequest("0.1.0", null, guidance));
    }

    private void reply(ObjectNode response) {
        Map<String, Object> values = json.convertValue(response, new TypeReference<>() {});
        when(intelligence.suggest(anyString(), any())).thenReturn(new TriageSuggestion(values));
    }

    private ObjectNode success() throws Exception {
        return (ObjectNode) json.readTree("""
                {
                  "contractVersion": "0.1.0", "workspaceId": "lab-a", "profile": "service-desk",
                  "requestId": "fixture", "status": "SUCCESS", "needsReview": false, "error": null,
                  "result": {"category": "NETWORK", "priority": "LOW", "suggestedGroup": "IT-NETWORK"},
                  "explanation": {
                    "impact": "I1", "urgency": "U2", "reasonCode": "CLASSIFIED", "text": "fixture",
                    "matchedRules": ["CAT-NET-001", "IMPACT-USER-001", "URGENCY-DEFERRED-001", "PRI-001"]
                  },
                  "evidence": [{"type": "INPUT", "field": "title", "text": "wifi"}],
                  "runtime": {"engine": "rules", "profileVersion": "service-desk@0.3.0", "rulesVersion": "0.3.0"}
                }
                """);
    }
}
