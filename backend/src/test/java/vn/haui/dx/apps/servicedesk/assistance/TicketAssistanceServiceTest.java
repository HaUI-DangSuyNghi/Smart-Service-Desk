package vn.haui.dx.apps.servicedesk.assistance;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import vn.haui.dx.apps.servicedesk.Ticket;
import vn.haui.dx.apps.servicedesk.TicketService;
import vn.haui.dx.hpdi.common.api.CapabilityException;
import vn.haui.dx.hpdi.intelligence.api.AssistanceOutcome;
import vn.haui.dx.hpdi.intelligence.api.GroundedAssistanceCapability;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TicketAssistanceServiceTest {
    private final TicketService tickets = mock(TicketService.class);
    private final GroundedAssistanceCapability intelligence = mock(GroundedAssistanceCapability.class);
    private final UUID id = UUID.randomUUID();
    private final ServiceDeskAssistanceConfiguration.Settings settings =
        new ServiceDeskAssistanceConfiguration.Settings(true, Map.of("scope-a", List.of()));
    private final TicketAssistanceService service = new TicketAssistanceService(tickets, intelligence, settings);

    @BeforeEach
    void setup() {
        when(tickets.get("scope-a", id)).thenReturn(new Ticket(
            id, "scope-a", "owner", UUID.randomUUID(), "Private ticket title",
            "Private description must not reach model by default", Instant.now(), null));
        when(intelligence.assist(anyString(), any())).thenReturn(new AssistanceOutcome(
            "0.1.0", UUID.randomUUID().toString(), "INSUFFICIENT_EVIDENCE", null, List.of(), true,
            "INSUFFICIENT_EVIDENCE"));
    }

    @Test
    void usesOnlyPublicCapabilityAndChecksTicketAgainWithoutMutatingWorkflow() {
        service.assist("scope-a", id, "device connection");

        var order = inOrder(tickets, intelligence);
        order.verify(tickets).get("scope-a", id);
        order.verify(intelligence).assist(eq("scope-a"), argThat(input ->
            input.question().equals("device connection")
                && input.profileVersion().equals("0.3.0")
                && input.policyIdentity().equals(ServiceDeskAssistanceConfiguration.POLICY)));
        order.verify(tickets).get("scope-a", id);
        verifyNoMoreInteractions(tickets, intelligence);
    }

    @Test
    void deniedTicketPreventsAnyAiAccess() {
        when(tickets.get("scope-a", id)).thenThrow(CapabilityException.notFound("Hidden"));
        assertThrows(CapabilityException.class, () -> service.assist("scope-a", id, "question"));
        verifyNoInteractions(intelligence);
    }

    @Test
    void ticketVisibilityChangedDuringInferenceSuppressesAnswer() {
        when(tickets.get("scope-a", id))
            .thenReturn(new Ticket(id, "scope-a", "owner", UUID.randomUUID(), "Title", "Description", Instant.now(), null))
            .thenThrow(CapabilityException.notFound("No longer visible"));

        assertThrows(CapabilityException.class, () -> service.assist("scope-a", id, "question"));
        verify(intelligence).assist(anyString(), any());
    }

    @Test
    void disabledOrUnconfiguredWorkspaceNeverCallsAi() {
        var disabled = new TicketAssistanceService(tickets, intelligence,
            new ServiceDeskAssistanceConfiguration.Settings(false, Map.of()));

        assertThrows(CapabilityException.class, () -> disabled.assist("scope-a", id, "question"));
        verifyNoInteractions(intelligence);
    }

    @Test
    void invalidQuestionsNeverReachAi() {
        for (String question : new String[] {null, " ", "x".repeat(2001), "control\u0000"}) {
            assertThrows(CapabilityException.class, () -> service.assist("scope-a", id, question));
        }
        verifyNoInteractions(intelligence);
    }
}
