package vn.haui.dx.apps.servicedesk.triage;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import vn.haui.dx.apps.servicedesk.Ticket;
import vn.haui.dx.apps.servicedesk.TicketService;
import vn.haui.dx.hpdi.common.api.CapabilityException;
import vn.haui.dx.hpdi.intelligence.api.IntelligenceCapability;
import vn.haui.dx.hpdi.intelligence.api.TriageSuggestion;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TicketTriageServiceTest {
    @Test void mapsOnlyPersistedTicketInputToThePublicCapability() {
        var tickets = mock(TicketService.class);
        UUID id = UUID.randomUUID();
        var ticket = new Ticket(id, "lab-a", "owner", UUID.randomUUID(), "Stored title", "Stored description", Instant.now(), null);
        when(tickets.get("lab-a", id)).thenReturn(ticket);
        IntelligenceCapability intelligence = (workspace, input) -> {
            assertEquals("lab-a", workspace);
            assertEquals("Stored title", input.title());
            assertEquals("Stored description", input.description());
            assertEquals("service-desk", input.profile());
            assertEquals("0.2.0", input.version());
            return new TriageSuggestion(Map.of("status", "SUCCESS"));
        };
        var service = new TicketTriageService(tickets, intelligence);
        assertEquals("SUCCESS", service.triage("lab-a", id).fields().get("status"));
        verify(tickets).get("lab-a", id);
        verifyNoMoreInteractions(tickets);
    }

    @Test void deniedReadNeverCallsAi() {
        var tickets = mock(TicketService.class);
        var intelligence = mock(IntelligenceCapability.class);
        when(tickets.get(anyString(), any())).thenThrow(CapabilityException.notFound("Ticket not found"));
        var service = new TicketTriageService(tickets, intelligence);
        assertThrows(CapabilityException.class, () -> service.triage("lab-a", UUID.randomUUID()));
        verifyNoInteractions(intelligence);
    }
}
